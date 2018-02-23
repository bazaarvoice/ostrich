package com.bazaarvoice.ostrich.pool;

import java.util.concurrent.TimeUnit;

/**
 * A policy for determining host service instance caching is performed by a {@link ServicePool}.  The
 * {@code ServiceCachingPolicy} configures how caching of should be performed by a {@link ServiceCache}.
 */
public interface ServiceCachingPolicy {
    /**
     * Returns the maximum number of in-use service instances that can exist globally.
     *
     * NOTE: A value of -1 indicates that there is no limit.
     *
     * @return the max num service instances
     */
    int getMaxNumServiceInstances();

    /**
     * Returns the maximum number of in-use service instances that can exist for a single end point.
     *
     * NOTE: A value of -1 indicates that there is no limit.
     *
     * @return the max num service instances per end point
     */
    int getMaxNumServiceInstancesPerEndPoint();

    /**
     * The amount of time that a service instance is allowed to be idle for before it can be expired from the cache.
     * An instance may still be evicted before this amount of time if the cache is full and needs to make room for a new
     * instance.
     *
     * NOTE: There is no guaranteed eviction time, so an idle service instance can be evicted as early as this time,
     * but not before.  A non-positive value indicates service instances will never be evicted based on idle time.
     *
     * @param unit the unit
     * @return the max service instance idle time
     */
    long getMaxServiceInstanceIdleTime(TimeUnit unit);

    /**
     * What action to take when it is not possible to allocate a new service instance because the cache is at its limit
     * for service instances.
     *
     * NOTE: Setting this to {@link ExhaustionAction#GROW} will make it so that the cache can (temporarily) hold more
     * instances than {@link #getMaxNumServiceInstances()} or {@link #getMaxNumServiceInstancesPerEndPoint()} says it
     * should be able to hold.
     *
     * @return the cache exhaustion action
     */
    ExhaustionAction getCacheExhaustionAction();

    /**
     * The enum Exhaustion action.
     */
    enum ExhaustionAction {
        /**
         * Throw an exception when at the limit of the number of allowed instances.
         */
        FAIL,

        /**
         * Create a new temporary service instance when at the limit of the number of allowed instances.
         */
        GROW,

        /**
         * Wait until an instance is returned to the cache when at the limit of the number of allowed instances.
         */
        WAIT
    }

    /**
     * This defaults to false, i.e. the default policy is to support a ServiceCache of single threaded clients.
     *
     * If this is set to true, all other params are ignored, and their getters throws unsupported operation exception
     *
     * @return true if policy is intended for multi threaded clients
     */
    boolean useMultiThreadedClientPolicy();
}
