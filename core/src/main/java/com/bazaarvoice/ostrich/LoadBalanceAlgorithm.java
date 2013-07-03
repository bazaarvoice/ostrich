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

public interface LoadBalanceAlgorithm {
    /**
     * Selects an end point to use based on a load balancing algorithm.  If no end point can be chosen, then
     * <code>null</code> is returned.
     *
     * @param endPoints The end points to choose from.
     * @param statistics Usage statistics about the end points in case the load balancing algorithm needs some
     *                   knowledge of the service pool's state.
     * @return Which end point to use or <code>null</code> if one couldn't be chosen.
     */
    ServiceEndPoint choose(Iterable<ServiceEndPoint> endPoints, ServicePoolStatistics statistics);
}
