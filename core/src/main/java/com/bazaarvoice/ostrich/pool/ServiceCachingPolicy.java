/*
 * Copyright 2013 Bazaarvoice, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
     * What action to take when it is not possible to allocate a new service instance because the cache is at its limit
     * for service instances.
     * <p/>
     * NOTE: Setting this to {@link ExhaustionAction#GROW} will make it so that the cache can (temporarily) hold more
     * instances than {@link #getMaxNumServiceInstances()} or {@link #getMaxNumServiceInstancesPerEndPoint()} says it
     * should be able to hold.
     */
    ExhaustionAction getCacheExhaustionAction();

    enum ExhaustionAction {
        /** Throw an exception when at the limit of the number of allowed instances. */
        FAIL,

        /** Create a new temporary service instance when at the limit of the number of allowed instances. */
        GROW,

        /** Wait until an instance is returned to the cache when at the limit of the number of allowed instances. */
        WAIT
    }
}
