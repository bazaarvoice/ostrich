package com.bazaarvoice.soa.zookeeper.dropwizard;

import org.codehaus.jackson.map.MappingJsonFactory;
import org.codehaus.jackson.map.ObjectMapper;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ZooKeeperConfigurationTest {
    private static final ObjectMapper JSON = new MappingJsonFactory().getCodec();

    @Test
    public void testJsonDefaults() throws IOException {
        ZooKeeperConfiguration config = fromJson("{}");
        assertEquals("localhost:2181", config.getConnectString());
        assertTrue(config.getRetryPolicy() instanceof com.netflix.curator.retry.BoundedExponentialBackoffRetry);
    }

    @Test
    public void testJsonConnectString() throws IOException {
        ZooKeeperConfiguration config = fromJson("{\"connectString\":\"prod-zk-1:12345\"}");
        assertEquals("prod-zk-1:12345", config.getConnectString());
    }

    @Test
    public void testJsonBoundedExponentialBackoffRetry() throws IOException {
        ZooKeeperConfiguration config = fromJson("{\"retry\":{\"baseSleepTimeMs\":100,\"maxSleepTimeMs\":1000,\"maxAttempts\":3}}");
        assertTrue(config.getRetryPolicy() instanceof com.netflix.curator.retry.BoundedExponentialBackoffRetry);
    }

    @Test
    public void testNamespace() throws IOException {
        ZooKeeperConfiguration config = fromJson("{\"namespace\":\"global\"}");
        assertEquals("global", config.getNamespace());
    }

    private ZooKeeperConfiguration fromJson(String json) throws IOException {
        return JSON.readValue(json, ZooKeeperConfiguration.class);
    }
}
