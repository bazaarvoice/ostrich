package com.bazaarvoice.soa.pool;

import com.bazaarvoice.soa.ServiceEndPoint;
import com.bazaarvoice.soa.ServiceFactory;
import com.bazaarvoice.soa.exceptions.InvalidEndPointCheckOutAttemptException;
import com.bazaarvoice.soa.exceptions.NoCachedConnectionAvailableException;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Throwables;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.apache.commons.pool.BaseKeyedPoolableObjectFactory;
import org.apache.commons.pool.impl.GenericKeyedObjectPool;

import java.io.Closeable;
import java.util.NoSuchElementException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * A cache for service instances. Useful if there's more than insignificant overhead in creating service connections
 * from a {@link ServiceEndPoint}.  Will spawn one thread (shared by all {@code ServiceCache}s) to handle evictions of
 */
class ServiceCache<S> implements Closeable {
    private static final ScheduledExecutorService EVICTION_EXECUTOR = Executors.newScheduledThreadPool(1,
            new ThreadFactoryBuilder()
                    .setNameFormat("ServiceCache-EvictionThread-%d")
                    .setDaemon(true)
                    .build());

    /** How often to try to evict old service instances. */
    @VisibleForTesting
    static final long EVICTION_DURATION_IN_SECONDS = 300;

    private final GenericKeyedObjectPool<ServiceEndPoint, S> _pool;
    private final Future<?> _evictionFuture;


    /**
     * Builds a basic service cache.
     *
     * @param policy         The configuration for this cache.
     * @param serviceFactory The factory to fall back to on cache misses.
     */
    ServiceCache(ServiceCachingPolicy policy, ServiceFactory<S> serviceFactory) {
        this(policy, serviceFactory, EVICTION_EXECUTOR);
    }

    /**
     * Builds a basic service cache.
     *
     * @param policy         The configuration for this cache.
     * @param serviceFactory The factory to fall back to on cache misses.
     * @param executor       The executor to use for checking for idle instances to evict.
     */
    @VisibleForTesting
    ServiceCache(ServiceCachingPolicy policy, ServiceFactory<S> serviceFactory, ScheduledExecutorService executor) {
        checkNotNull(policy);
        checkNotNull(serviceFactory);
        checkNotNull(executor);

        GenericKeyedObjectPool.Config poolConfig = new GenericKeyedObjectPool.Config();

        // Global configuration
        poolConfig.maxTotal = policy.getMaxNumServiceInstances();
        poolConfig.numTestsPerEvictionRun = policy.getMaxNumServiceInstances();
        poolConfig.minEvictableIdleTimeMillis = policy.getMinIdleTimeBeforeEviction(TimeUnit.MILLISECONDS);

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

        // Per endpoint configuration
        poolConfig.maxActive = policy.getMaxNumServiceInstancesPerEndPoint();
        poolConfig.maxIdle = policy.getMaxNumServiceInstancesPerEndPoint();

        _pool = new GenericKeyedObjectPool<ServiceEndPoint, S>(new PoolServiceFactory<S>(serviceFactory), poolConfig);
        _evictionFuture = executor.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                try {
                    evict();
                } catch (Exception e) {
                    // TODO: Log?
                }
            }
        }, EVICTION_DURATION_IN_SECONDS, EVICTION_DURATION_IN_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * Retrieves a cached service instance for an endpoint that is not currently checked out.  If no idle cached
     * instance is available and the cache is not full, a new one will be created, added to the cache, and then checked
     * out.  Once the checked out instance is no longer in use, it should be returned by calling {@link #checkIn}.
     *
     * @param endPoint The end point to retrieve a cached service instance for.
     * @return A cached service instance for the requested end point.
     * @throws NoCachedConnectionAvailableException
     *          If the cache has reached total maximum capacity, or maximum capacity
     *          for the requested end point, and no connections that aren't already checked out are available.
     * @throws InvalidEndPointCheckOutAttemptException
     *          the predicate
     */
    public S checkOut(ServiceEndPoint endPoint) {
        checkNotNull(endPoint);

        try {
            return _pool.borrowObject(endPoint);
        } catch (NoSuchElementException e) {
            // This will happen if there are no available connections and there is no room for a new one,
            // or if a newly created connection is not valid.
            throw new NoCachedConnectionAvailableException();
        } catch (Exception e) {
            // Should only happen if new service creation is attempted and service factory throws an exception.
            throw Throwables.propagate(e);
        }
    }

    /**
     * Returns a service instance for an endpoint to the cache so that it may be used by other users.
     *
     * @param endPoint The end point that the service instance belongs to.
     * @param service  The service instance to return to the pool.
     */
    public void checkIn(ServiceEndPoint endPoint, S service) {
        checkNotNull(endPoint);
        checkNotNull(service);

        try {
            _pool.returnObject(endPoint, service);
        } catch (Exception e) {
            // Should never happen.
            throw Throwables.propagate(e);
        }
    }

    @Override
    public void close() {
        // TODO: Transition states and don't permit usage after closing?
        _evictionFuture.cancel(false);
        _pool.clear();
    }

    public void evict(ServiceEndPoint endPoint) {
        checkNotNull(endPoint);

        _pool.clear(endPoint);
    }

    private void evict() throws Exception {
        _pool.evict();
    }

    private static class PoolServiceFactory<S> extends BaseKeyedPoolableObjectFactory<ServiceEndPoint, S> {
        private final ServiceFactory<S> _serviceFactory;

        public PoolServiceFactory(ServiceFactory<S> serviceFactory) {
            _serviceFactory = serviceFactory;
        }

        @Override
        public S makeObject(ServiceEndPoint endPoint) throws Exception {
            return _serviceFactory.create(endPoint);
        }
    }
}