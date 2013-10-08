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

import com.bazaarvoice.ostrich.ServiceEndPoint;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.junit.Test;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;

public class RandomAlgorithmTest {
    @Test(expected = NullPointerException.class)
    public void testNullIterable() {
        new RandomAlgorithm().choose(null, null);
    }

    @Test
    public void testEmptyIterable() {
        ServiceEndPoint endPoint = new RandomAlgorithm().choose(Collections.<ServiceEndPoint>emptyList(), null);
        assertNull(endPoint);
    }

    @Test
    public void testAllElementsCanBeSelected() {
        RandomAlgorithm algorithm = new RandomAlgorithm();

        List<ServiceEndPoint> endPoints = Lists.newArrayList(
                mock(ServiceEndPoint.class),
                mock(ServiceEndPoint.class),
                mock(ServiceEndPoint.class),
                mock(ServiceEndPoint.class),
                mock(ServiceEndPoint.class)
        );

        // Make 1000 independent choices.  This ensures that we'll see all of the elements with a probability
        // of 1-5(4/5)^1000.  If we ran this test once we would expect to see a failure every 5e88 years.
        // For our purposes that's close enough to 100%.
        Set<ServiceEndPoint> seen = Sets.newIdentityHashSet();
        for (int i = 0; i < 1000; i++) {
            seen.add(algorithm.choose(endPoints, null));
        }

        assertEquals(endPoints.size(), seen.size());
    }
}
