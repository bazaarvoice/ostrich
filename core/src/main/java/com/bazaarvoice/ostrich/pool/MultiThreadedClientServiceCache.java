package com.bazaarvoice.ostrich.pool;

import com.bazaarvoice.ostrich.ServiceEndPoint;
import com.bazaarvoice.ostrich.ServiceFactory;
import com.bazaarvoice.ostrich.ThreadSafeServiceFactory;
import com.bazaarvoice.ostrich.metrics.Metrics;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalCause;
import com.google.common.cache.RemovalListener;
import com.google.common.cache.RemovalNotification;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

/**
 * A cache for "heavy weight" client instances, i.e. ones that are already thread safe.
 * Therefore as a {@code ServiceCache} we can just map Endpoints to single instances of those
 * "heavy weight" clients.
 * 
 * This applies to third party client libraries for connecting to generic or specialized
 * services, i.e. HttpClient, JestClient, etc.
 * 
 * This cache does no actual caching. It lies to {@code ServicePool}, the consumer of ServiceCache,
 * mimics the behavior of cache, and returns singleton instance of service maintained in
 * a map, keyed with{@code ServiceEndPoint}
 *
 * @param <S> the Service type
 */
public class MultiThreadedClientServiceCache<S> implements ServiceCache<S> {
    private static final Logger LOG = LoggerFactory.getLogger(MultiThreadedClientServiceCache.class);
    /**
     * Note on volatile:
     * The instancesPerEndpoint map holds the singleton service handles per every unique
     * key constructed from endpoint name and id. It is HashMap, chosen for its performance.
     * However since updates can throw ConcurrentModificationException when updated by two
     * threads simultaneously, copy-on-write is chosen to counteract that.
     *
     * Which raises another problem, where the object might have different states in (cpu) cache
     * and (main) memory as a result of assignment from one thread and simultaneous read from
     * another thread, resulting in discrepancy of retrieved data across threads. Therefore to
     * counteract that it is declared as volatile, such that it will never be cached and must
     * be read from and written back to the (main) memory on any and every access. There is a
     * performance penalty associated with volatile, but that is negligible.
     *
     * Read more at: http://jeremymanson.blogspot.com/2008/05/double-checked-locking.html
     *
     * In practice this ensures that the assignment operation is atomic across threads which is
     * much desirable for copy-on-write operation.
     *
     * The same argument goes to all other volatile variables
     *
     * Note on evictedList:
     * The evicted list is basically a guava cache with a configurable ttl to gracefully clean
     * up evicted service handles. Note that, this does not prevent a consumer thread to request
     * an evicted endpoint after another thread marked it as evicted. This is left up to the
     * consumer to not request an evicted endpoint. This does however guarantees that an endpoint
     * marked for eviction will eventually be marked for deletion, and will be deleted. This delay
     * allows existing threads who already have them checked out, finish up their task gracefully
     * before destroying the endpoint service handle.
     *
     * Note on deletedList:
     * From evicted list an endpoint goes to deleted list, or re-registering an endpoint moves
     * the old instance handle to deleted list. This list has a static wait time of 30 second
     * before the cleanup thread destroys the associated service handle.
     */
    private volatile Map<ServiceEndPoint, ServiceHandle<S>> _instancesPerEndpoint;
    private volatile boolean _isClosed;

    // for tracking when was the last time a client was created for an end point
    private final Map<ServiceEndPoint, Long> _mostRecentClientCreationTimePerEndpoint;
    private final Cache<ServiceEndPoint, ServiceHandle<S>> _evictedList;

    // for maintaining the list of items marked for deletion
    private final AtomicLong _deletionCounter;
    private final Cache<Long, ServiceHandle<S>> _toBeDeletedList;

    // timers to report register and evict events
    private final Timer _registerTimer;
    private final Timer _evictionTimer;
    private final Metrics.InstanceMetrics _metrics;

    // factory to deal with service handles
    private final ServiceFactory<S> _serviceFactory;

    // default time in seconds before the scheduled job cleans up the deleted items
    private static final int DEFAULT_CLEANUP_DELAY = 30;

    // time to wait before registering the same endpoint already created by other thread
    private static final long DUP_REGISTRATION_WINDOW = 1000l; // 1 second

    // scheduled cleanup job executor
    private final Future<?> _cleanupFuture;
    private static final ScheduledExecutorService CLEANUP_EXECUTOR =
            Executors.newScheduledThreadPool(1, new ThreadFactoryBuilder()
                    .setNameFormat("ServiceCache-CleanupThread-%d")
                    .setDaemon(true)
                    .build());


    /**
     * Builds a MultiThreadedClientServiceCache. Used by the builder, used defaults
     * for {@code ScheduledExecutorService} and {@code cleanupDelay}
     *
     * @param serviceFactory            the service factory for creating service handles
     * @param evictionDurationInSeconds the eviction duration (in seconds) to keep evicted handles around
     * @param metricRegistry            the metric registry for reporting metrics
     */
    MultiThreadedClientServiceCache(ThreadSafeServiceFactory<S> serviceFactory, int evictionDurationInSeconds,
                                    MetricRegistry metricRegistry) {
        this(serviceFactory, CLEANUP_EXECUTOR, evictionDurationInSeconds, DEFAULT_CLEANUP_DELAY, metricRegistry);
    }

    /**
     * Builds a MultiThreadedClientServiceCache.
     *
     * @param serviceFactory            the service factory for creating service handles
     * @param executor                  the executor for creating the eviction list (cache) cleanup thread
     * @param evictionDurationInSeconds the eviction duration (in seconds) to keep evicted handles around
     * @param metricRegistry            the metric registry for reporting metrics
     */
    MultiThreadedClientServiceCache(ThreadSafeServiceFactory<S> serviceFactory, ScheduledExecutorService executor,
                                    int evictionDurationInSeconds, int cleanUpDelayInSeconds, MetricRegistry metricRegistry) {
        checkNotNull(serviceFactory);
        checkNotNull(metricRegistry);
        checkArgument(evictionDurationInSeconds >= 0);
        checkArgument(cleanUpDelayInSeconds >= 0);

        // This is COW because we are dealing with extremely large number of reads and very few writes
        // also, this is read from checkout(), therefore we're trying to keep it lightweight
        _instancesPerEndpoint = Maps.newHashMap();
        // reads and writes are fairly equal and this is not read from checkout()
        _mostRecentClientCreationTimePerEndpoint = Maps.newConcurrentMap();
        _serviceFactory = serviceFactory;
        _isClosed = false;

        _evictedList = CacheBuilder.newBuilder().expireAfterWrite(evictionDurationInSeconds, TimeUnit.SECONDS)
                .removalListener(createEvictionListener()).build();

        _toBeDeletedList = CacheBuilder.newBuilder().expireAfterWrite(cleanUpDelayInSeconds, TimeUnit.SECONDS)
                .removalListener(createDeletionListener()).build();

        _cleanupFuture = executor.scheduleAtFixedRate(createCleanupRunnableTask(), DEFAULT_CLEANUP_DELAY, DEFAULT_CLEANUP_DELAY, TimeUnit.SECONDS);

        _deletionCounter = new AtomicLong();
        _metrics = Metrics.forInstance(metricRegistry, this, serviceFactory.getServiceName());
        _registerTimer = _metrics.timer("register-time");
        _evictionTimer = _metrics.timer("eviction-time");
    }

    /**
     * Mimics the behavior of a cache check in, actually a NOOP
     * 
     * Since there are no multiple service handle and their associated versions
     * to maintain, check in in free!
     *
     * @param handle The service handle that is being checked in.
     * @throws NullPointerException if the handle is null
     */
    @Override
    public void checkIn(ServiceHandle<S> handle) throws Exception {
        checkNotNull(handle);
    }

    /**
     * check out the instance of service handle.
     * if its not registered, registers it synchronously and then returns the newly created handle
     *
     * @param endPoint The end point to retrieve the instance of service handle
     * @return the service handle
     * @throws IllegalStateException if the cache is closed
     * @throws NullPointerException if endpoint is null
     */
    @Override
    public ServiceHandle<S> checkOut(ServiceEndPoint endPoint) throws Exception {
        checkNotNull(endPoint);
        checkState(!_isClosed, "cache is closed");
        ServiceHandle<S> instanceHandle = _instancesPerEndpoint.get(endPoint);
        if (instanceHandle == null) {
            return doRegister(endPoint);
        }
        return instanceHandle;
    }

    /**
     * Private registration method that is used by checkout() and register()
     *
     * @param endPoint the end point
     * @return the service handle
     */
    private synchronized ServiceHandle<S> doRegister(ServiceEndPoint endPoint) {
        checkNotNull(endPoint);

        ServiceHandle<S> oldServiceHandle = _instancesPerEndpoint.get(endPoint);
        if(oldServiceHandle == null || isEndPointOld(endPoint)) {
            S service = _serviceFactory.create(endPoint);
            ServiceHandle<S> newServiceHandle = new ServiceHandle<>(service, endPoint);
            _instancesPerEndpoint = cowAddToMap(endPoint, newServiceHandle);
            _mostRecentClientCreationTimePerEndpoint.put(endPoint, System.currentTimeMillis());
            if (oldServiceHandle != null) {
                putInDeletedList(oldServiceHandle);
            }
            return newServiceHandle;
        }
        else {
            return oldServiceHandle;
        }
    }

    /**
     * This registers an endpoint in the cache if its not already registered
     * This also removes the same from evicted list if existed and puts it in
     * deleted list
     *
     * @param endPoint to register on the cache
     */
    @Override
    public synchronized void register(ServiceEndPoint endPoint) {
        checkNotNull(endPoint);
        Timer.Context context = _registerTimer.time();
        doRegister(endPoint);
        ServiceHandle<S> evictedServiceHandle = _evictedList.getIfPresent(endPoint);
        if(evictedServiceHandle != null) {
            _evictedList.invalidate(endPoint);
        }
        context.stop();
    }

    /**
     * This evicts an endpoint, subsequent calls to checkout on the endPoint will
     * fail with {@code EndPointEvictedException} unless that endPoint is registered again
     *
     * @param endPoint to evict from the cache
     */
    @Override
    public synchronized void evict(ServiceEndPoint endPoint) {
        checkNotNull(endPoint);
        Timer.Context context = _evictionTimer.time();
        if(_instancesPerEndpoint.containsKey(endPoint)) {
            ServiceHandle<S> serviceHandle = _instancesPerEndpoint.get(endPoint);
            _evictedList.put(endPoint, serviceHandle);
        }
        context.stop();
    }

    /**
     * Mark the cache as closed to prevent further checkouts, clean up resource
     */
    @Override
    public synchronized void close() {
        _isClosed = true;
        for (ServiceHandle<S> serviceHandle : _instancesPerEndpoint.values()) {
            putInDeletedList(serviceHandle);
        }
        _instancesPerEndpoint = Maps.newHashMap();
        _mostRecentClientCreationTimePerEndpoint.clear();
        _evictedList.invalidateAll();
        _toBeDeletedList.invalidateAll();
        _cleanupFuture.cancel(false);
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
     * Destroys a service handle gracefully, swallows any exception
     *
     * We only remove from the timestamp map if the timestamp is old and
     * therefore has no value  aka
     * (System.currentTimeMillis() > lastUpdated + DUP_REGISTRATION_WINDOW)
     *
     * @param serviceHandle the service handle
     */
    private void destroyService(ServiceHandle<S> serviceHandle) {
        if (serviceHandle != null) {
            try {
                if (_mostRecentClientCreationTimePerEndpoint.containsKey(serviceHandle.getEndPoint())
                        && isEndPointOld(serviceHandle.getEndPoint())) {
                    _mostRecentClientCreationTimePerEndpoint.remove(serviceHandle.getEndPoint());
                }
                _serviceFactory.destroy(serviceHandle.getEndPoint(), serviceHandle.getService());
            }
            catch (Exception e) {
                LOG.error("Service handle destroy failed for end point: " + serviceHandle.getEndPoint(), e);
            }
        }
    }

    /**
     * Put in deleted items queue
     *
     * @param handle the handle to add the the queue of deleted items for the end point
     */
    private void putInDeletedList(ServiceHandle<S> handle) {
        _toBeDeletedList.put(_deletionCounter.getAndIncrement(), handle);
    }

    /**
     * Creates a removal listener for toBeEvictedList
     *
     * On receiving a removal notification, this will explicitly destroy the handle if the
     * notification was {@code EXPIRED}
     * Otherwise, it'll move the handle to the toBeDeleted list for future removal
     *
     * @return the removal listener
     */
    private RemovalListener<ServiceEndPoint, ServiceHandle<S>> createEvictionListener() {
        return new RemovalListener<ServiceEndPoint, ServiceHandle<S>>() {
            @Override
            public void onRemoval(RemovalNotification<ServiceEndPoint, ServiceHandle<S>> notification) {
                ServiceEndPoint endPoint = notification.getKey();
                ServiceHandle<S> handle = notification.getValue();
                // we modify the instancesPerEndPoint on EXPIRED only,
                // but we always just mark the handle for deletion
                if(RemovalCause.EXPIRED.equals(notification.getCause())) {
                    // don't need double check lock, this will be single threaded
                    synchronized (this) {
                        _instancesPerEndpoint = cowRemoveFromMap(endPoint);
                    }
                }
                putInDeletedList(handle);
            }
        };
    }

    /**
     * Creates a removal listener for toBeDeletedList
     *
     * @return the removal listener
     */
    private RemovalListener<Long, ServiceHandle<S>> createDeletionListener() {
        return new RemovalListener<Long, ServiceHandle<S>>() {
            @Override
            public void onRemoval(RemovalNotification<Long, ServiceHandle<S>> notification) {
                ServiceHandle<S> handle = notification.getValue();
                destroyService(handle);
            }
        };
    }

    /**
     * Creates a runnable object used for scheduled cleanup
     *
     * Try to cleanup, error should never happen, but log just in case.
     * Swallow exception so thread doesn't die.
     *
     * @return the runnable object
     */
    private Runnable createCleanupRunnableTask() {
        return new Runnable() {
            @Override
            public void run() {
                try {
                    _evictedList.cleanUp();
                } catch (Exception e) {
                    LOG.error("eviction list cleanup failed", e);
                }
                try {
                    _toBeDeletedList.cleanUp();
                } catch (Exception e) {
                    LOG.error("deletion list cleanup failed", e);
                }
            }
        };
    }

    /**
     * Checks if a client for an end point is created before the DUP_REGISTRATION_WINDOW
     *
     * There are inherent race conditions from the zookeper discovery, and we
     * do not want to create multiple clients for the same endpoint because of
     * it. Hence we are checking the mostRecentClientCreationTimePerEndpoint
     * map to see if it was created within the DUP_REGISTRATION_WINDOW or not
     *
     * @param endPoint the end point
     * @return the boolean
     */
    private boolean isEndPointOld(ServiceEndPoint endPoint) {
        Long lastUpdated = _mostRecentClientCreationTimePerEndpoint.get(endPoint);
        // not having a timestamp ==> not old
        // current time > timestamp + wait-window ==> old
        return (lastUpdated != null && (System.currentTimeMillis() > lastUpdated + DUP_REGISTRATION_WINDOW));

    }

    /**
     * Copy on write helper method to add a endPoint-handle pair to non-synchronized _instancesPerEndpoint map
     *
     * @param endPoint    the endPoint to put
     * @param handle  the handle to put
     * @return a new map with the requested add
     */
    private Map<ServiceEndPoint, ServiceHandle<S>> cowAddToMap(ServiceEndPoint endPoint, ServiceHandle<S> handle) {
        checkNotNull(endPoint);
        checkNotNull(handle);
        Map<ServiceEndPoint, ServiceHandle<S>> sourceCopy = Maps.newHashMap(_instancesPerEndpoint);
        sourceCopy.put(endPoint, handle);
        return sourceCopy;
    }

    /**
     * Copy on write helper method to remove endPoint from non-synchronized _instancesPerEndpoint map
     *
     * @param endPoint    the endPoint to remove
     * @return a new map with the requested remove
     */
    private Map<ServiceEndPoint, ServiceHandle<S>> cowRemoveFromMap(ServiceEndPoint endPoint) {
        checkNotNull(endPoint);
        Map<ServiceEndPoint, ServiceHandle<S>> sourceCopy = Maps.newHashMap(_instancesPerEndpoint);
        sourceCopy.remove(endPoint);
        return sourceCopy;
    }
}
