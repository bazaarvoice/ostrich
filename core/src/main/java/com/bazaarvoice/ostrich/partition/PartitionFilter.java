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
package com.bazaarvoice.ostrich.partition;

import com.bazaarvoice.ostrich.PartitionContext;
import com.bazaarvoice.ostrich.ServiceEndPoint;
import com.bazaarvoice.ostrich.ServicePool;

/**
 * Filters a set of end points based on a {@link PartitionContext} object.
 */
public interface PartitionFilter {
    /**
     * Filters a set of end points based on a {@link PartitionContext} object.
     *
     * @param endPoints A collection of end points.  Known (or suspected) bad end points have been removed.
     * @param partitionContext The {@link com.bazaarvoice.ostrich.PartitionContext} object passed to the
     *                         {@link ServicePool#execute} method.
     * @return A collection of end points that may service the specified partition.  This might be the same object
     *         passed in the {@code endPoints} argument if all end points may service the specified partition.
     */
    Iterable<ServiceEndPoint> filter(Iterable<ServiceEndPoint> endPoints, PartitionContext partitionContext);
}
