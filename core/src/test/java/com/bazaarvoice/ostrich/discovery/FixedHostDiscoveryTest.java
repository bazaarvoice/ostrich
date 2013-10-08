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
package com.bazaarvoice.ostrich.discovery;

import com.bazaarvoice.ostrich.ServiceEndPoint;
import com.bazaarvoice.ostrich.ServiceEndPointBuilder;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.io.Closeables;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class FixedHostDiscoveryTest {
    private static final ServiceEndPoint FOO = new ServiceEndPointBuilder()
            .withServiceName("Foo")
            .withId("server:8080")
            .build();

    private static final ServiceEndPoint BAR = new ServiceEndPointBuilder()
            .withServiceName("Bar")
            .withId("server:8081")
            .build();

    private FixedHostDiscovery _discovery;

    @Before
    public void setup() throws Exception {
        _discovery = new FixedHostDiscovery(FOO);
    }

    @After
    public void teardown() throws Exception {
        Closeables.closeQuietly(_discovery);
    }

    @Test(expected = NullPointerException.class)
    public void testNullEndPoint() {
        new FixedHostDiscovery((ServiceEndPoint) null);
    }

    @Test(expected = NullPointerException.class)
    public void testNullEndPoints() {
        new FixedHostDiscovery(Arrays.asList(FOO, BAR, null));
    }

    @Test(expected = NullPointerException.class)
    public void testNullEndPointArray() {
        new FixedHostDiscovery((ServiceEndPoint[]) null);
    }

    @Test(expected = NullPointerException.class)
    public void testNullEndPointIterable() {
        new FixedHostDiscovery((Iterable<ServiceEndPoint>) null);
    }

    @Test
    public void testClose() throws IOException {
        _discovery.close();
    }

    @Test
    public void testAddListener() {
        // Verify it doesn't blow up
        _discovery.addListener(null);
    }

    @Test
    public void testRemoveListener() {
        // Verify it doesn't blow up
        _discovery.removeListener(null);
    }

    @Test
    public void testIgnoresChanges() throws Exception {
        List<ServiceEndPoint> endPoints = Lists.newArrayList(FOO);
        FixedHostDiscovery discovery = new FixedHostDiscovery(endPoints);

        // Change the backing list, verify it doesn't affect FixedHostDiscovery
        endPoints.remove(FOO);
        assertEquals(1, Iterables.size(discovery.getHosts()));

        endPoints.add(BAR);
        assertEquals(1, Iterables.size(discovery.getHosts()));
    }
}
