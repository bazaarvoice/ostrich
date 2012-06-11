package com.bazaarvoice.soa.zookeeper;

import org.codehaus.jackson.map.MappingJsonFactory;
import org.codehaus.jackson.map.ObjectMapper;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ZooKeeperConfigurationTest {
    private static final ObjectMapper JSON = new MappingJsonFactory().getCodec();

    @Test
    public void testNullExponentialBackoffRetry() {
        new ExponentialBackoffRetry(0, 0);
    }

    @Test
    public void testNullRetryNTimes() {
        new RetryNTimes(0, 0);
    }

    @Test
    public void testNullRetryUntilElapsed() {
        new RetryUntilElapsed(0, 0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testBadExponentialBackoffRetry() {
        new ExponentialBackoffRetry(0, 1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testBadRetryNTimes() {
        new RetryNTimes(1, 0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testBadRetryUntilElapsed() {
        new RetryUntilElapsed(1, 0);
    }

    @Test
    public void testJsonDefaults() throws IOException {
        ZooKeeperConfiguration config = fromJson("{}");
        assertEquals("localhost:2181", config.getConnectString());
        assertTrue(config.getRetryPolicy() instanceof RetryNTimes);
    }

    @Test
    public void testJsonConnectString() throws IOException {
        ZooKeeperConfiguration config = fromJson("{\"connectString\":\"prod-zk-1:12345\"}");
        assertEquals("prod-zk-1:12345", config.getConnectString());
    }

    @Test
    public void testJsonExponentialBackoffRetry() throws IOException {
        ZooKeeperConfiguration config = fromJson("{\"exponentialBackoffRetry\":{\"baseSleepTimeMs\":100,\"maxRetries\":3}}");
        assertTrue(config.getRetryPolicy() instanceof ExponentialBackoffRetry);
    }

    @Test
    public void testJsonRetryNTimes() throws IOException {
        ZooKeeperConfiguration config = fromJson("{\"retryNTimes\":{\"sleepMsBetweenRetries\":100,\"n\":3}}");
        assertTrue(config.getRetryPolicy() instanceof RetryNTimes);
    }

    @Test
    public void testJsonRetryUntilElapsed() throws IOException {
        ZooKeeperConfiguration config = fromJson("{\"retryUntilElapsed\":{\"sleepMsBetweenRetries\":100,\"maxElapsedTimeMs\":2000}}");
        assertTrue(config.getRetryPolicy() instanceof RetryUntilElapsed);
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
