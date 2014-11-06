package com.bazaarvoice.ostrich.pool;

import java.util.concurrent.TimeUnit;

/**
 * A policy for determining host service instance caching is performed by a {@link ServicePool}.  The
 * {@code ServiceCachingPolicy} configures how caching of should be performed by a {@link ServiceCache}.
 */
public interface ServiceCachingPolicy {
    /**
     * Returns the maximum number of in-use service instances that can exist globally.
     * <p/>
     * NOTE: A value of -1 indicates that there is no limit.
     */
    int getMaxNumServiceInstances();

    /**
     * Returns the maximum number of in-use service instances that can exist for a single end point.
     * <p/>
     * NOTE: A value of -1 indicates that there is no limit.
     */
    int getMaxNumServiceInstancesPerEndPoint();

    /**
     * The amount of time that a service instance is allowed to be idle for before it can be expired from the cache.
     * An instance may still be evicted before this amount of time if the cache is full and needs to make room for a new
     * instance.
     * <p/>
     * NOTE: There is no guaranteed eviction time, so an idle service instance can be evicted as early as this time,
     * but not before.  A non-positive value indicates service instances will never be evicted based on idle time.
     */
    long getMaxServiceInstanceIdleTime(TimeUnit unit);

    /**
     * Whether or not to block when it is not possible to allocate a new service instance because the cache is at its limit
     * for service instances.
     */
    boolean getBlockWhenExhausted();

    /**
     *
     * Previously there was a way to specify exhaustAction as specified in the enum below
     *
     *  enum ExhaustionAction {FAIL,GROW,WAIT}
     *
     * However, ever since updating to commons-pool-2.2 we lost that ability and are restricted to only
     * block if exhausted OR fail, as set in {@code GenericKeyedObjectPoolConfig#setBlockWhenExhausted(boolean)}
     *
     */
}
