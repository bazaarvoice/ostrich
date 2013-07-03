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
package com.bazaarvoice.ostrich.examples.dictionary.client;

import com.bazaarvoice.ostrich.PartitionContext;
import com.bazaarvoice.ostrich.ServiceEndPoint;
import com.bazaarvoice.ostrich.partition.PartitionFilter;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;

import static com.google.common.base.Preconditions.checkNotNull;

public class DictionaryPartitionFilter implements PartitionFilter {
    @Override
    public Iterable<ServiceEndPoint> filter(Iterable<ServiceEndPoint> endPoints, PartitionContext partitionContext) {
        final String word = (String) checkNotNull(partitionContext.get());

        return Iterables.filter(endPoints, new Predicate<ServiceEndPoint>() {
            @Override
            public boolean apply(ServiceEndPoint endPoint) {
                return Payload.valueOf(endPoint.getPayload()).getPartition().apply(word);
            }
        });
    }
}
