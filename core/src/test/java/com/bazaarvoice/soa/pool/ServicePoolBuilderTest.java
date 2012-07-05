package com.bazaarvoice.soa.pool;

import com.bazaarvoice.soa.*;
import com.bazaarvoice.soa.ServicePool;
import com.bazaarvoice.soa.zookeeper.ZooKeeperConfiguration;
import com.bazaarvoice.soa.zookeeper.ZooKeeperConnection;
import com.google.common.io.Closeables;
import com.netflix.curator.test.TestingServer;
import org.junit.Test;

import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.ScheduledExecutorService;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ServicePoolBuilderTest {
    @Test(expected = NullPointerException.class)
    public void testNullHostDiscovery() {
        ServicePoolBuilder.create(Service.class).withHostDiscovery(null);
    }

    @Test(expected = NullPointerException.class)
    public void testNullZooKeeperConnection() {
        ServicePoolBuilder.create(Service.class).withZooKeeperHostDiscovery(null);
    }

    @Test(expected = NullPointerException.class)
    public void testNullServiceFactory() {
        ServicePoolBuilder.create(Service.class).withServiceFactory(null);
    }

    @Test(expected = NullPointerException.class)
    public void testNullHealthCheckExecutor() {
        ServicePoolBuilder.create(Service.class).withHealthCheckExecutor(null);
    }

    @SuppressWarnings("unchecked")
    @Test(expected = IllegalStateException.class)
    public void testBuildWithNoHostDiscoveryAndNoZooKeeperConnection() {
        ServiceFactory<Service> serviceFactory = (ServiceFactory<Service>) mock(ServiceFactory.class);
        ScheduledExecutorService healthCheckExecutor = mock(ScheduledExecutorService.class);
        ServicePoolBuilder.create(Service.class)
                .withServiceFactory(serviceFactory)
                .withHealthCheckExecutor(healthCheckExecutor)
                .build();
    }

    @Test(expected = NullPointerException.class)
    public void testBuildWithNoServiceFactory() {
        HostDiscovery hostDiscovery = mock(HostDiscovery.class);
        ScheduledExecutorService healthCheckExecutor = mock(ScheduledExecutorService.class);
        ServicePoolBuilder.create(Service.class)
                .withHostDiscovery(hostDiscovery)
                .withHealthCheckExecutor(healthCheckExecutor)
                .build();
    }

    @SuppressWarnings("unchecked")
    @Test(expected = NullPointerException.class)
    public void testBuildWithNullLoadBalanceAlgorithm() {
        HostDiscovery hostDiscovery = mock(HostDiscovery.class);
        ServiceFactory<Service> serviceFactory = (ServiceFactory<Service>) mock(ServiceFactory.class);
        ScheduledExecutorService healthCheckExecutor = mock(ScheduledExecutorService.class);

        ServicePoolBuilder.create(Service.class)
                .withServiceFactory(serviceFactory)
                .withHostDiscovery(hostDiscovery)
                .withHealthCheckExecutor(healthCheckExecutor)
                .build();
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testBuildWithZooKeeperConnection() throws Exception {
        ServiceFactory<Service> serviceFactory = (ServiceFactory<Service>) mock(ServiceFactory.class);
        when(serviceFactory.getServiceName()).thenReturn("serviceName");
        when(serviceFactory.getLoadBalanceAlgorithm()).thenReturn(mock(LoadBalanceAlgorithm.class));

        TestingServer zooKeeperServer = new TestingServer();
        ZooKeeperConnection connection = null;
        try {
            connection = new ZooKeeperConfiguration().withConnectString(zooKeeperServer.getConnectString()).connect();

            ServicePoolBuilder.create(Service.class)
                    .withServiceFactory(serviceFactory)
                    .withZooKeeperHostDiscovery(connection)
                    .build();
        } finally {
            Closeables.closeQuietly(connection);
            Closeables.closeQuietly(zooKeeperServer);
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testHostDiscoverySourceOverride() {
        ServiceFactory<Service> serviceFactory = (ServiceFactory<Service>) mock(ServiceFactory.class);
        when(serviceFactory.getLoadBalanceAlgorithm()).thenReturn(mock(LoadBalanceAlgorithm.class));

        final HostDiscovery overrideDiscovery = mock(HostDiscovery.class);
        HostDiscovery baseDiscovery = mock(HostDiscovery.class);
        ServicePool<Service> pool = ServicePoolBuilder.create(Service.class)
                .withHostDiscoverySource(new HostDiscoverySource() {
                    @Override
                    public HostDiscovery forService(String serviceName) {
                        return overrideDiscovery;
                    }
                })
                .withHostDiscovery(baseDiscovery)
                .withServiceFactory(serviceFactory)
                .build();
        assertEquals(overrideDiscovery, ((com.bazaarvoice.soa.pool.ServicePool) pool).getHostDiscovery());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testHostDiscoverySourceFallThrough() {
        ServiceFactory<Service> serviceFactory = (ServiceFactory<Service>) mock(ServiceFactory.class);
        when(serviceFactory.getLoadBalanceAlgorithm()).thenReturn(mock(LoadBalanceAlgorithm.class));

        HostDiscovery hostDiscovery = mock(HostDiscovery.class);
        ServicePool<Service> pool = ServicePoolBuilder.create(Service.class)
                .withHostDiscoverySource(new HostDiscoverySource() {
                    @Override
                    public HostDiscovery forService(String serviceName) {
                        return null;
                    }
                })
                .withHostDiscovery(hostDiscovery)
                .withServiceFactory(serviceFactory)
                .build();
        assertEquals(hostDiscovery, ((com.bazaarvoice.soa.pool.ServicePool) pool).getHostDiscovery());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testBuildWithNoHealthCheckExecutor() {
        LoadBalanceAlgorithm loadBalanceAlgorithm = mock(LoadBalanceAlgorithm.class);
        HostDiscovery hostDiscovery = mock(HostDiscovery.class);

        ServiceFactory<Service> serviceFactory = (ServiceFactory<Service>) mock(ServiceFactory.class);
        when(serviceFactory.getLoadBalanceAlgorithm()).thenReturn(loadBalanceAlgorithm);

        ServicePoolBuilder.create(Service.class)
                .withServiceFactory(serviceFactory)
                .withHostDiscovery(hostDiscovery)
                .build();
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testBuildWithNoTicker() {
        LoadBalanceAlgorithm loadBalanceAlgorithm = mock(LoadBalanceAlgorithm.class);
        HostDiscovery hostDiscovery = mock(HostDiscovery.class);
        ScheduledExecutorService healthCheckExecutor = mock(ScheduledExecutorService.class);

        ServiceFactory<Service> serviceFactory = (ServiceFactory<Service>) mock(ServiceFactory.class);
        when(serviceFactory.getLoadBalanceAlgorithm()).thenReturn(loadBalanceAlgorithm);

        ServicePoolBuilder.create(Service.class)
                .withServiceFactory(serviceFactory)
                .withHostDiscovery(hostDiscovery)
                .withHealthCheckExecutor(healthCheckExecutor)
                .build();
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testBuildProxy() throws IOException {
        ServiceFactory serviceFactory = mock(ServiceFactory.class);
        when(serviceFactory.getLoadBalanceAlgorithm()).thenReturn(mock(LoadBalanceAlgorithm.class));
        Service service = ServicePoolBuilder.create(Service.class)
                .withHostDiscovery(mock(HostDiscovery.class))
                .withServiceFactory(serviceFactory)
                .buildProxy(mock(RetryPolicy.class));
        assertTrue(service instanceof Closeable);
    }

    // A dummy interface for testing...
    private static interface Service {}
}
