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
package com.bazaarvoice.ostrich;

/**
 * A provider of statistics relating to the state of the {@link ServicePool}. Mainly useful for making decisions for
 * load balancing, a {@code ServicePool} will pass an instance to the {@link ServiceFactory} when requesting a
 * {@link LoadBalanceAlgorithm}.
 */
public interface ServicePoolStatistics {
    /**
     * The number of cached service instances not currently in use for a single end point.
     * @param endPoint The end point to get cache data for.
     * @return The number of idle service instances in the cache for the given end point.
     */
    int getNumIdleCachedInstances(ServiceEndPoint endPoint);

    /**
     * The number of service instances in the pool currently being used to execute callbacks for a single end point.
     * Note that this only represents that activity between a single service pool and the end point, and does not in any
     * way represent activity of other service pools for the same service, other applications connected to the service,
     * or global overall load for the service.
     * @param endPoint The end point to get activity data for.
     * @return The number of service instances actively serving callbacks for the given end point.
     */
    int getNumActiveInstances(ServiceEndPoint endPoint);
}
