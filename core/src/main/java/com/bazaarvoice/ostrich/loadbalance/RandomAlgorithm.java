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
package com.bazaarvoice.ostrich.loadbalance;

import com.bazaarvoice.ostrich.LoadBalanceAlgorithm;
import com.bazaarvoice.ostrich.ServiceEndPoint;
import com.bazaarvoice.ostrich.ServicePoolStatistics;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

import java.util.Iterator;
import java.util.List;
import java.util.Random;

public class RandomAlgorithm implements LoadBalanceAlgorithm {
    private final Random _rnd = new Random();

    @Override
    public ServiceEndPoint choose(Iterable<ServiceEndPoint> endPoints, ServicePoolStatistics statistics) {
        Preconditions.checkNotNull(endPoints);

        Iterator<ServiceEndPoint> iter = endPoints.iterator();
        if (!iter.hasNext()) {
            return null;
        }

        List<ServiceEndPoint> list = Lists.newArrayList(iter);
        if (list.size() == 1) {
            return list.get(0);
        }
        return list.get(_rnd.nextInt(list.size()));
    }
}
