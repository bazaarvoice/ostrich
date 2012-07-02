package com.bazaarvoice.soa.discovery;

import com.bazaarvoice.soa.ServiceEndPoint;
import com.bazaarvoice.soa.ServiceEndPointBuilder;
import com.google.common.collect.Lists;
import com.google.common.io.Closeables;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

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
    public void testMembershipCheck() {
        assertTrue(_discovery.contains(FOO));
        assertFalse(_discovery.contains(BAR));
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
        endPoints.add(BAR);

        assertTrue(discovery.contains(FOO));
        assertFalse(discovery.contains(BAR));
    }
}
