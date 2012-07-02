package com.bazaarvoice.soa.discovery;

import com.bazaarvoice.soa.HostDiscovery;
import com.bazaarvoice.soa.HostDiscoverySource;
import com.bazaarvoice.soa.ServiceEndPoint;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class ConfiguredFixedHostDiscoverySourceTest {
    @Test(expected = NullPointerException.class)
    public void testNullMap() {
        new ConfiguredFixedHostDiscoverySource<Void>(null);
    }

    @Test
    public void testEmptyMap() {
        HostDiscoverySource source = new ConfiguredFixedHostDiscoverySource<Void>();
        assertNull(source.forService("payload"));
    }

    @Test
    public void testNonEmptyMap() {
        Map<String, String> endPoints = ImmutableMap.of("id", "payload");
        HostDiscoverySource source = new ConfiguredFixedHostDiscoverySource<String>(endPoints);
        assertNotNull(source.forService("service"));
    }

    @Test
    public void testSingleEndPoint() {
        Map<String, String> endPoints = ImmutableMap.of("id", "payload");
        HostDiscoverySource source = new ConfiguredFixedHostDiscoverySource<String>(endPoints);
        HostDiscovery hostDiscovery = source.forService("service");
        assertNotNull(hostDiscovery);

        List<ServiceEndPoint> hosts = Lists.newArrayList(hostDiscovery.getHosts());
        assertEquals(1, hosts.size());

        ServiceEndPoint endPoint = hosts.get(0);
        assertEquals("service", endPoint.getServiceName());
        assertEquals("id", endPoint.getId());
        assertEquals("payload", endPoint.getPayload());
    }
}
