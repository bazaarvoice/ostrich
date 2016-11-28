package com.bazaarvoice.ostrich.pool;

import com.bazaarvoice.ostrich.ServiceEndPoint;
import com.bazaarvoice.ostrich.ServiceFactory;
import com.bazaarvoice.ostrich.exceptions.NoCachedInstancesAvailableException;
import com.bazaarvoice.ostrich.metrics.Metrics;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.RatioGauge;
import com.codahale.metrics.Timer;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.MapMaker;
import org.apache.commons.pool.BaseKeyedPoolableObjectFactory;
import org.apache.commons.pool.impl.GenericKeyedObjectPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static com.bazaarvoice.ostrich.pool.ServiceCacheBuilder.buildDefaultExecutor;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * A cache for service instances. Useful if there's more than insignificant overhead in creating service connections
 * from a {@link com.bazaarvoice.ostrich.ServiceEndPoint}.  Will spawn one thread (shared by all
 * {@link com.bazaarvoice.ostrich.pool.ServiceCache}s) to handle evictions of
 */
class SingleThreadedClientServiceCache<S> implements ServiceCache<S> {
    private static final Logger LOG = LoggerFactory.getLogger(SingleThreadedClientServiceCache.class);

    /** How often to try to evict old service instances. */
    @VisibleForTesting
    static final long EVICTION_DURATION_IN_SECONDS = 300;

    private final GenericKeyedObjectPool<ServiceEndPoint, S> _pool;
    private final AtomicLong _revisionNumber = new AtomicLong();
    private final Map<ServiceEndPoint, Long> _invalidRevisions = new MapMaker().weakKeys().makeMap();
    private final Map<ServiceHandle<S>, Long> _checkedOutRevisions = new MapMaker().makeMap();
    private final Future<?> _evictionFuture;
    private volatile boolean _isClosed = false;
    private final Metrics.InstanceMetrics _metrics;
    private final Timer _loadTimer;
    private final AtomicLong _requestCount = new AtomicLong();
    private final AtomicLong _missCount = new AtomicLong();
    private final AtomicLong _loadSuccessCount = new AtomicLong();
    private final AtomicLong _loadFailureCount = new AtomicLong();

    /**
     * Builds a basic service cache.
     *
     * @param policy         The configuration for this cache.
     * @param serviceFactory The factory to fall back to on cache misses.
     * @param metrics        The metric registry.
     */
    SingleThreadedClientServiceCache(ServiceCachingPolicy policy, ServiceFactory<S> serviceFactory, MetricRegistry metrics) {
        this(policy, serviceFactory, buildDefaultExecutor(), metrics);
    }

    /**
     * Builds a basic service cache.
     *
     * @param policy         The configuration for this cache.
     * @param serviceFactory The factory to fall back to on cache misses.
     * @param executor       The executor to use for checking for idle instances to evict.
     */
    @VisibleForTesting
    SingleThreadedClientServiceCache(ServiceCachingPolicy policy, ServiceFactory<S> serviceFactory, ScheduledExecutorService executor,
                                     MetricRegistry metrics) {
        checkNotNull(policy);
        checkNotNull(serviceFactory);
        checkNotNull(executor);

        String serviceName = serviceFactory.getServiceName();
        _metrics = Metrics.forInstance(metrics, this, serviceName);
        _loadTimer = _metrics.timer("load-time");

        _metrics.gauge("cache-hit-ratio", new RatioGauge() {
            @Override
            protected Ratio getRatio() {
                return Ratio.of(_requestCount.get() - _missCount.get(), _requestCount.get());
            }
        });

        _metrics.gauge("cache-miss-ratio", new RatioGauge() {
            @Override
            protected Ratio getRatio() {
                return Ratio.of(_missCount.get(), _requestCount.get());
            }
        });

        _metrics.gauge("load-success-ratio", new RatioGauge() {
            @Override
            protected Ratio getRatio() {
                return Ratio.of(_loadSuccessCount.get(), _loadSuccessCount.get() + _loadFailureCount.get());
            }
        });
        _metrics.gauge("load-failure-ratio", new RatioGauge() {
            @Override
            protected Ratio getRatio() {
                return Ratio.of(_loadFailureCount.get(), _loadSuccessCount.get() + _loadFailureCount.get());
            }
        });

        GenericKeyedObjectPool.Config poolConfig = new GenericKeyedObjectPool.Config();

        // Global configuration
        poolConfig.maxTotal = policy.getMaxNumServiceInstances();
        poolConfig.numTestsPerEvictionRun = policy.getMaxNumServiceInstances();
        poolConfig.minEvictableIdleTimeMillis = policy.getMaxServiceInstanceIdleTime(TimeUnit.MILLISECONDS);

        switch (policy.getCacheExhaustionAction()) {
            case FAIL:
                poolConfig.whenExhaustedAction = GenericKeyedObjectPool.WHEN_EXHAUSTED_FAIL;
                break;
            case GROW:
                poolConfig.whenExhaustedAction = GenericKeyedObjectPool.WHEN_EXHAUSTED_GROW;
                break;
            case WAIT:
                poolConfig.whenExhaustedAction = GenericKeyedObjectPool.WHEN_EXHAUSTED_BLOCK;
                break;
        }

        // Per end point configuration
        poolConfig.maxActive = policy.getMaxNumServiceInstancesPerEndPoint();
        poolConfig.maxIdle = policy.getMaxNumServiceInstancesPerEndPoint();

        // Make sure all instances in the pool are checked for staleness during eviction runs.
        poolConfig.numTestsPerEvictionRun = policy.getMaxNumServiceInstances();

        _pool = new GenericKeyedObjectPool<>(new PoolServiceFactory<>(serviceFactory), poolConfig);

        // Don't schedule eviction if not caching or not expiring stale instances.
        _evictionFuture = (policy.getMaxNumServiceInstances() != 0)
                || (policy.getMaxNumServiceInstancesPerEndPoint() != 0)
                || (policy.getMaxServiceInstanceIdleTime(TimeUnit.MILLISECONDS) > 0)
                ? executor.scheduleAtFixedRate(new Runnable() {
                      @Override
                      public void run() {
                          try {
                              _pool.evict();
                          } catch (Exception e) {
                              // Should never happen, but log just in case. Swallow exception so thread doesn't die.
                              LOG.error("ServiceCache eviction run failed.", e);
                          }
                      }
                  }, EVICTION_DURATION_IN_SECONDS, EVICTION_DURATION_IN_SECONDS, TimeUnit.SECONDS)
                : null;
    }

    @VisibleForTesting
    GenericKeyedObjectPool<ServiceEndPoint, S> getPool() {
        return _pool;
    }

    /**
     * Retrieves a cached service instance for an end point that is not currently checked out.  If no idle cached
     * instance is available and the cache is not full, a new one will be created, added to the cache, and then checked
     * out.  Once the checked out instance is no longer in use, it should be returned by calling {@link #checkIn}.
     *
     * @param endPoint The end point to retrieve a cached service instance for.
     * @return A service handle that contains a cached service instance for the requested end point.
     * @throws NoCachedInstancesAvailableException If the cache has reached total maximum capacity, or maximum capacity
     *         for the requested end point, and no connections that aren't already checked out are available.
     */
    public ServiceHandle<S> checkOut(ServiceEndPoint endPoint) throws Exception {
        checkNotNull(endPoint);
        _requestCount.incrementAndGet();

        try {
            long revision = _revisionNumber.incrementAndGet();
            S service = _pool.borrowObject(endPoint);
            ServiceHandle<S> handle = new ServiceHandle<>(service, endPoint);

            // Remember the revision that we've checked this service out on in case we need to invalidate it later
            _checkedOutRevisions.put(handle, revision);

            return handle;
        } catch (NoSuchElementException e) {
            _missCount.incrementAndGet();

            // This will happen if there are no available connections and there is no room for a new one,
            // or if a newly created connection is not valid.
            throw new NoCachedInstancesAvailableException(String.format("No cached instances available for endpoint: %s", endPoint));
        }
    }

    /**
     * Returns a service instance for an end point to the cache so that it may be used by other users.
     *
     * @param handle The service handle that is being checked in.
     * @throws Exception Never.
     */
    @Override
    public void checkIn(ServiceHandle<S> handle) throws Exception {
        checkNotNull(handle);

        S service = handle.getService();
        ServiceEndPoint endPoint = handle.getEndPoint();

        // Figure out if we should check this revision in.  If it was created before the last known invalid revision
        // for this particular end point, or the cache is closed, then we shouldn't check it in.
        Long invalidRevision = _invalidRevisions.get(endPoint);
        Long serviceRevision = _checkedOutRevisions.remove(handle);

        if (_isClosed || (invalidRevision != null && serviceRevision < invalidRevision)) {
            _pool.invalidateObject(endPoint, service);
        } else {
            _pool.returnObject(endPoint, service);
        }
    }

    @Override
    public int getNumIdleInstances(ServiceEndPoint endPoint) {
        checkNotNull(endPoint);
        return _pool.getNumIdle(endPoint);
    }

    @Override
    public int getNumActiveInstances(ServiceEndPoint endPoint) {
        checkNotNull(endPoint);
        return _pool.getNumActive(endPoint);
    }

    @Override
    public void close() {
        _isClosed = true;

        if (_evictionFuture != null) {
            _evictionFuture.cancel(false);
        }

        try {
            _pool.close();
        }
        catch(Exception e) {
            LOG.error("Error closing pool", e);
        }
        _metrics.close();
    }

    @Override
    public void register(ServiceEndPoint endPoint) {
        // implementation of the ServiceCache creates clients lazily
    }

    @Override
    public void evict(ServiceEndPoint endPoint) {
        checkNotNull(endPoint);

        // Mark all service instances created prior to now as invalid so that we don't inadvertently check them back in
        _invalidRevisions.put(endPoint, _revisionNumber.incrementAndGet());
        _pool.clear(endPoint);
    }

    private class PoolServiceFactory<S> extends BaseKeyedPoolableObjectFactory<ServiceEndPoint, S> {
        private final ServiceFactory<S> _serviceFactory;

        public PoolServiceFactory(ServiceFactory<S> serviceFactory) {
            _serviceFactory = serviceFactory;
        }

        @Override
        public S makeObject(final ServiceEndPoint endPoint) throws Exception {
            _missCount.incrementAndGet();

            Timer.Context timer = _loadTimer.time();
            try {
                S service = _serviceFactory.create(endPoint);
                _loadSuccessCount.incrementAndGet();
                return service;
            } catch (Exception e) {
                _loadFailureCount.incrementAndGet();
                throw e;
            } finally {
                timer.stop();
            }
        }

        @Override
        public void destroyObject(ServiceEndPoint endPoint, S service) throws Exception {
            _serviceFactory.destroy(endPoint, service);
        }
    }
}
