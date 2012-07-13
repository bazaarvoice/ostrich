package com.bazaarvoice.soa.pool;

import com.bazaarvoice.soa.ServiceEndPoint;
import com.bazaarvoice.soa.ServiceFactory;
import com.bazaarvoice.soa.exceptions.InvalidEndPointCheckOutAttemptException;
import com.bazaarvoice.soa.exceptions.NoCachedConnectionAvailableException;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Predicate;
import com.google.common.base.Throwables;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.apache.commons.pool.KeyedPoolableObjectFactory;
import org.apache.commons.pool.impl.GenericKeyedObjectPool;

import java.io.Closeable;
import java.util.NoSuchElementException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * A cache for service connections. Useful if there's more than insignificant overhead in creating service connections
 * from a {@link ServiceEndPoint}. Will spawn one thread (shared by all {@code ServiceCache}s to handle cache evictions.
 * @param <S>
 */
class ServiceCache<S> implements Closeable {

    private final GenericKeyedObjectPool<ServiceEndPoint, S> _pool;
    private final ServiceFactory<S> _serviceFactory;
    private final Predicate<ServiceEndPoint> _isValid;
    private final Future<?> _evictionFuture;

    /**
     * Builds a basic service cache.
     * @param serviceFactory The factory to fall back to on cache misses.
     * @param policy The configuration for this cache.
     * @param isValid The predicate to check {@code ServiceEndPoint} validity. Invalid end points will be dropped from
     * the cache on {@link #checkIn}, and calls to {@link #checkOut} on invalid end points will fail.
     */
    ServiceCache(ServiceFactory<S> serviceFactory, ServiceCachingPolicy policy, Predicate<ServiceEndPoint> isValid) {
        checkNotNull(serviceFactory);
        checkNotNull(policy);
        checkNotNull(isValid);

        final GenericKeyedObjectPool.Config poolConfig = new GenericKeyedObjectPool.Config();
        poolConfig.maxActive = (int) policy._maxConnectionsPerEndPoint;
        poolConfig.maxIdle = (int) policy._maxConnectionsPerEndPoint;
        poolConfig.maxTotal = (int) policy._maxTotalConnections;
        poolConfig.minEvictableIdleTimeMillis = policy._unit.toMillis(policy._minConnectionIdleTimeBeforeEviction);
        poolConfig.whenExhaustedAction = GenericKeyedObjectPool.WHEN_EXHAUSTED_FAIL;
        poolConfig.testOnBorrow = true;
        poolConfig.testOnReturn = true;
        poolConfig.testWhileIdle = false;
        poolConfig.numTestsPerEvictionRun = poolConfig.maxTotal;

        _pool = new GenericKeyedObjectPool<ServiceEndPoint, S>(new PoolableFactory(), poolConfig);
        _serviceFactory = serviceFactory;
        _isValid = isValid;
        _evictionFuture = EvictorScheduler.schedule(this, policy._idleConnectionEvictionFrequency, policy._unit);
    }

    /**
     * Retrieves a cached service connection for an endpoint that is not currently checked out. If no idle cached
     * connection is available and the cache is not full, a new one will be created, add to the cache, and then checked
     * out. Once the checked out connection is no longer in use, it should be returned by calling {@link #checkIn}.
     * @param endPoint The end point to retrieve a cached connection for.
     * @return A cached service connection for the requested end point.
     * @throws NoCachedConnectionAvailableException If the cache has reached total maximum capacity, or maximum capacity
     * for the requested end point, and no connections that aren't already checked out are available.
     * @throws InvalidEndPointCheckOutAttemptException the predicate
     */
    public S checkOut(ServiceEndPoint endPoint) {
        checkNotNull(endPoint);
        try {
            return _pool.borrowObject(endPoint);
        } catch (NoSuchElementException e) {
            if (!_isValid.apply(endPoint)) {
                throw new InvalidEndPointCheckOutAttemptException();
            }
            // This will happen if there are no available connections and there is no room for a new one,
            // or if a newly created connection is not valid.
            throw new NoCachedConnectionAvailableException();
        } catch (Exception e) {
            // Should only happen if new service creation is attempted and service factory throws an exception.
            throw Throwables.propagate(e);
        }
    }

    public void checkIn(ServiceEndPoint endPoint, S service) {
        try {
            _pool.returnObject(endPoint, service);
        } catch (Exception e) {
            // Should never happen.
        }
    }

    @Override
    public void close() {
        _evictionFuture.cancel(false);
    }

    void endPointRemoved(ServiceEndPoint endPoint) {
        _pool.clear(endPoint);
    }

    @VisibleForTesting
    void evict() throws Exception {
        _pool.evict();
    }

    private class PoolableFactory implements KeyedPoolableObjectFactory<ServiceEndPoint, S> {

        @Override
        public S makeObject(ServiceEndPoint endPoint)
                throws Exception {
            return _serviceFactory.create(endPoint);
        }

        @Override
        public void destroyObject(ServiceEndPoint endPoint, S s)
                throws Exception {
        }

        @Override
        public boolean validateObject(ServiceEndPoint endPoint, S s) {
            return _isValid.apply(endPoint);
        }

        @Override
        public void activateObject(ServiceEndPoint endPoint, S s)
                throws Exception {
        }

        @Override
        public void passivateObject(ServiceEndPoint endPoint, S s)
                throws Exception {
        }
    }

    // Handles scheduling eviction runs. Ensures there is only one eviction thread shared for all caches.
    private static class EvictorScheduler {

        private final static ScheduledExecutorService EXECUTOR = Executors.newScheduledThreadPool(1,
                        new ThreadFactoryBuilder()
                                .setNameFormat("ServiceCacheEvictor%d")
                                .setDaemon(true)
                                .build()
                        );

        public static Future<?> schedule(final ServiceCache<?> cache, long delay, TimeUnit timeUnit) {
            Runnable evictor = new Runnable() {
                @Override
                public void run() {
                    try {
                        cache.evict();
                    } catch (Exception e) {
                        // Ignored.
                    }
                }
            };
            return EXECUTOR.scheduleAtFixedRate(evictor, delay, delay, timeUnit);
        }
    }
}