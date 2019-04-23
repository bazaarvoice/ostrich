package com.bazaarvoice.ostrich.pool;

import com.bazaarvoice.ostrich.MultiThreadedServiceFactory;
import com.bazaarvoice.ostrich.ServiceEndPoint;
import com.bazaarvoice.ostrich.ServiceFactory;
import com.bazaarvoice.ostrich.metrics.Metrics;
import com.codahale.metrics.Counter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Maps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static com.bazaarvoice.ostrich.pool.ServiceCacheBuilder.buildDefaultExecutor;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * A ServiceCache for "heavy weight" client instances, i.e. ones that are already thread safe.
 * Therefore unlike {@link com.bazaarvoice.ostrich.pool.SingleThreadedClientServiceCache}, we
 * can just map EndPoints to a single shared instance of a "heavy weight" client.
 * <p/>
 * This applies to third party client libraries for connecting to generic or specialized
 * services, i.e. {@code HttpClient}, {@code JestClient}, {@code ElasticSearchClient} etc.
 * <p/>
 * If your client library is multi-thread safe, this ServiceCache should provide better
 * performance than the {@link com.bazaarvoice.ostrich.pool.SingleThreadedClientServiceCache}.
 *
 * @param <S> the Service type
 */
public class MultiThreadedClientServiceCache<S> implements ServiceCache<S> {
    private static final Logger LOG = LoggerFactory.getLogger(MultiThreadedClientServiceCache.class);

    // Grace period during which a new service instance will not be replaced by subsequent registrations
    // of the same end point.
    private static final long DUP_REGISTRATION_WINDOW_MILLIS = SECONDS.toMillis(1);
    private static final int DEFAULT_CLEANUP_DELAY_SECONDS = 15;
    private static final int DEFAULT_EVICTION_DELAY_SECONDS = (int) MINUTES.toSeconds(3);

    /**
     * We want to be able to perform more than 300 checkOuts and checkIns per second.
     * Thus we would like those methods to use an un-synchronized / non-blocking Map implementation.
     * <p/>
     * In order to do that we use a Copy-On-Write approach where all modifications
     * (new EndPoint, EndPoint goes away, etc) will create a new Map instance.  This is acceptable as
     * EndPoint addition / removal events are rare compared to checkIns and checkOuts.
     * <p/>
     * Because we are changing the pointer that is "_instancesPerEndpoint", we have to use the volatile
     * keyword to force any thread that accesses the map to have the latest reference to it. There is a
     * performance penalty associated with volatile, but it is better than synchronization.
     */
    private volatile Map<ServiceEndPoint, HeavyServiceHandle<S>> _instancesPerEndpoint = Maps.newHashMap();
    private volatile boolean _isClosed;

    private final Metrics.InstanceMetrics _metrics;
    private final Timer _registerTimer;
    private final Timer _evictionTimer;
    private final Counter _serviceCounter;
    private final ServiceFactory<S> _serviceFactory;
    private final Future<?> _cleanupFuture;
    private final ScheduledExecutorService _cleanupExecutor;
    private final long _evictionDelayInMilliSeconds;

    /**
     * ServiceHandle that also tracks eviction and freshness status
     */
    private class HeavyServiceHandle<T extends S> extends ServiceHandle<S> {

        private final long _sellByDate;
        private volatile long _expireAfterDate = Long.MAX_VALUE;

        public HeavyServiceHandle(T service, ServiceEndPoint endPoint) {
            super(service, endPoint);

            _sellByDate = System.currentTimeMillis() + DUP_REGISTRATION_WINDOW_MILLIS;
        }

        public boolean isOld() {
            return System.currentTimeMillis() > _sellByDate;
        }

        public boolean hasBeenFlaggedForEviction() {
            return _expireAfterDate != Long.MAX_VALUE;
        }

        public boolean timeToEvict() {
            return _expireAfterDate != Long.MAX_VALUE && System.currentTimeMillis() > _expireAfterDate;
        }

        public void flagAsEvicted() {
            if (_expireAfterDate == Long.MAX_VALUE) {
                // Not synchronized - evictionDelay is long enough that thread contention here shouldn't matter
                _expireAfterDate = System.currentTimeMillis() + _evictionDelayInMilliSeconds;
            }
        }
    }

    /**
     * Builds a {@code MultiThreadedClientServiceCache} with a default executor and cleanup delay.
     * Used by the builder.
     *
     * @param serviceFactory The service factory for creating service handles
     * @param metricRegistry The metric registry for reporting metrics
     */
    MultiThreadedClientServiceCache(MultiThreadedServiceFactory<S> serviceFactory, MetricRegistry metricRegistry) {
        this(serviceFactory, buildDefaultExecutor(), DEFAULT_EVICTION_DELAY_SECONDS, DEFAULT_CLEANUP_DELAY_SECONDS, metricRegistry);
    }

    /**
     * Builds a {@code MultiThreadedClientServiceCache} with configurable eviction and cleanUp delays.
     *
     * @param serviceFactory         The service factory for creating service handles
     * @param executor               The executor for creating the eviction list (cache) cleanup thread
     * @param evictionDelayInSeconds how long to keep evicted handles around
     * @param cleanUpDelayInSeconds  how long to wait before scheduled cleanup
     * @param metricRegistry         The metric registry for reporting metrics
     */
    @VisibleForTesting
    MultiThreadedClientServiceCache(MultiThreadedServiceFactory<S> serviceFactory, ScheduledExecutorService executor,
                                    int evictionDelayInSeconds, int cleanUpDelayInSeconds, MetricRegistry metricRegistry) {
        checkNotNull(serviceFactory);
        checkNotNull(metricRegistry);
        checkArgument(evictionDelayInSeconds >= 0);
        checkArgument(cleanUpDelayInSeconds >= 0);

        _serviceFactory = serviceFactory;
        _cleanupExecutor = executor;
        _evictionDelayInMilliSeconds = SECONDS.toMillis(evictionDelayInSeconds);
        _isClosed = false;

        _metrics = Metrics.forInstance(metricRegistry, this, serviceFactory.getServiceName());
        _registerTimer = _metrics.timer("register-time");
        _evictionTimer = _metrics.timer("eviction-time");
        _serviceCounter = _metrics.counter("service-counter");

        _cleanupFuture = _cleanupExecutor.scheduleAtFixedRate(
                new Runnable() {
                    @Override
                    public void run() {
                        List<HeavyServiceHandle<S>> handlesToDelete = new LinkedList<>();
                        // Don't synchronize on just 'this', as 'this' is a anonymous inner Runnable class.
                        // Instead synchronize on our parent class instance of MultiThreadedClientServiceCache so that
                        // this code will be synchronized with evict() and doRegister().
                        synchronized (MultiThreadedClientServiceCache.this) {
                            Map<ServiceEndPoint, HeavyServiceHandle<S>> sourceCopy = Maps.newHashMap(_instancesPerEndpoint);
                            // Purge evicted instances from the copy-on-write map
                            for (HeavyServiceHandle<S> handle : _instancesPerEndpoint.values()) {
                                if (handle.timeToEvict()) {
                                    handlesToDelete.add(sourceCopy.remove(handle.getEndPoint()));
                                }
                            }
                            _instancesPerEndpoint = sourceCopy;
                        }
                        // Outside the synchronization loop, schedule the ServiceHandles for deletion
                        for (HeavyServiceHandle<S> handle : handlesToDelete) {
                            destroyService(handle);
                        }
                        _serviceCounter.dec(handlesToDelete.size());
                    }
                },
                // In our unit tests we want to set the cleanup timeout to "zero", but executorService
                // does not like that, so we set it to at least a millisecond.
                SECONDS.toMillis(cleanUpDelayInSeconds) + 1,
                SECONDS.toMillis(cleanUpDelayInSeconds) + 1,
                TimeUnit.MILLISECONDS);
    }

    /**
     * Mimics the behavior of a cache check in, actually a NO-OP.
     * <p/>
     * Since the {@code MultiThreadedClientServiceCache} does not have multiple service handles for an endPoint
     *
     * @param handle The service handle that is being checked in.
     * @throws NullPointerException if the handle is null
     */
    @Override
    public void checkIn(ServiceHandle<S> handle) throws Exception {
        checkNotNull(handle);
    }

    /**
     * Given an ServiceEndpoint return a ServiceHandle.
     * <p/>
     * If a ServiceHandle does not exist for the given ServiceEndpoint, this method will
     * synchronously create one and return it.
     *
     * @param endPoint The end point to retrieve the instance of service handle for
     * @return the service handle
     * @throws IllegalStateException if the cache is closed
     * @throws NullPointerException  if endPoint is null
     */
    @Override
    public ServiceHandle<S> checkOut(ServiceEndPoint endPoint) throws Exception {
        checkNotNull(endPoint);
        checkState(!_isClosed, "cache is closed");
        ServiceHandle<S> serviceHandle = _instancesPerEndpoint.get(endPoint);
        if (serviceHandle == null) {
            // This is the non-ideal state, as we now have to call a synchronized
            // method to create the ServiceHandle and update the copy-on-write map.
            //
            // Note this can/will happen when new Endpoints are discovered due to the
            // inherent race conditions in ServicePool and HostDiscovery.
            return doRegister(endPoint);
        }

        // Note we are not checking if the serviceHandle has been flagged for Eviction, as
        //  there are race conditions between checkOut() and ServiceCache.evict().
        return serviceHandle;
    }

    /**
     * Private registration method that is used by checkout() and register().
     *
     * @param endPoint the end point
     * @return the service handle
     */
    private ServiceHandle<S> doRegister(ServiceEndPoint endPoint) {
        checkNotNull(endPoint);

        ServiceHandle<S> toDelete = null;
        ServiceHandle<S> toReturn;

        synchronized (this) {
            HeavyServiceHandle<S> existingServiceHandle = _instancesPerEndpoint.get(endPoint);
            if (existingServiceHandle == null || existingServiceHandle.hasBeenFlaggedForEviction() || existingServiceHandle.isOld()) {

                // If there was not an existingServiceHandle, then make a new one.
                //
                // If existingServiceHandle.hasBeenFlaggedForEviction() is true, that means this EndPoint
                //  has been "evicted" for being "bad" but has recovered before the Eviction timeout
                //  process has gotten around to cleaning up this serviceHandle.  In this case, we assume
                //  the "safest" thing to do is to create a new Client for that EndPoint, in case the
                //  problem was with the "old" client.
                //
                // If the existingServiceHandle is "new" don't create a new client object due to the
                //  race condition in HostDiscovery and ServicePool, which can cause a checkOut() to
                //  occur before its associated ServiceCache.register().
                // Thus we want have a short period of time where duplicate "checkouts" and a register
                //  will not thrash the system creating a series of Client instances.
                //
                // _serviceFactory.create(endPoint) is a potentially expensive operation, memory, file handles, etc.
                // hence we really only want to do it when we have to, preferably via the out-of-band
                // ServiceCache.register() method, instead of the high traffic checkOut method.
                S service = _serviceFactory.create(endPoint);
                _serviceCounter.inc();

                HeavyServiceHandle<S> newServiceHandle = new HeavyServiceHandle<>(service, endPoint);

                // add the newServiceHandle to a new copy of the _instancesPerEndpoint map
                Map<ServiceEndPoint, HeavyServiceHandle<S>> sourceCopy = Maps.newHashMap(_instancesPerEndpoint);
                sourceCopy.put(endPoint, newServiceHandle);
                _instancesPerEndpoint = sourceCopy;

                toDelete = existingServiceHandle;
                toReturn = newServiceHandle;
            } else {
                // The existingServiceHandle was not null, not evicted, and not old, thus we did not recreate it.
                toReturn = existingServiceHandle;
            }
        }

        // Destroy instances outside the synchronization, with the idea being it may be an expensive operation
        if (toDelete != null) {
            destroyService(toDelete);
            _serviceCounter.dec();
        }

        return toReturn;
    }

    @Override
    public void register(ServiceEndPoint endPoint) {
        checkNotNull(endPoint);

        Timer.Context context = _registerTimer.time();
        try {
            doRegister(endPoint);
        } catch (Exception ex) {
            LOG.error("Error registering service handle", ex);
        } finally {
            context.stop();
        }
    }

    @Override
    public synchronized void evict(ServiceEndPoint endPoint) {
        checkNotNull(endPoint);
        Timer.Context context = _evictionTimer.time();

        // This method is synchronized, as even though we are not swapping the copy-on-write map,
        // we are still modifying its contents a bit.
        HeavyServiceHandle<?> serviceHandle = _instancesPerEndpoint.get(endPoint);
        if (serviceHandle != null) {
            serviceHandle.flagAsEvicted();
        }
        context.stop();
    }

    @Override
    public synchronized void close() {
        _isClosed = true;

        for (ServiceHandle<S> serviceHandle : _instancesPerEndpoint.values()) {
            _serviceFactory.destroy(serviceHandle.getEndPoint(), serviceHandle.getService());
        }
        _instancesPerEndpoint = Maps.newHashMap();
        _cleanupFuture.cancel(false);
        _cleanupExecutor.shutdownNow();
        _metrics.close();
    }

    /**
     * As these clients are multi threaded single instance, there's always one available
     *
     * @param endPoint to find idle instance count
     * @return 1 if endPoint is registered, 0 otherwise
     */
    @Override
    public int getNumIdleInstances(ServiceEndPoint endPoint) {
        checkNotNull(endPoint);
        return _instancesPerEndpoint.containsKey(endPoint) ? 1 : 0;
    }

    /**
     * This does not track if an instance is actively being used, however given its
     * singleton nature but it is safe to assume it is always being used
     *
     * @param endPoint to find active instance count
     * @return 1 if endPoint is registered, 0 otherwise
     */
    @Override
    public int getNumActiveInstances(ServiceEndPoint endPoint) {
        checkNotNull(endPoint);
        return _instancesPerEndpoint.containsKey(endPoint) ? 1 : 0;
    }

    /**
     * Destroys a service handle quietly, swallows any exception occurred
     *
     * @param serviceHandle to destroy
     */
    private void destroyService(ServiceHandle<S> serviceHandle) {
        try {
            _serviceFactory.destroy(serviceHandle.getEndPoint(), serviceHandle.getService());
        } catch (Exception e) {
            // this should not happen, but if it does, swallow the exception and log it
            LOG.warn("Error destroying serviceHandle", e);
        }
    }
}
