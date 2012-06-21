package com.bazaarvoice.soa.pool;

import com.bazaarvoice.soa.HostDiscovery;
import com.bazaarvoice.soa.LoadBalanceAlgorithm;
import com.bazaarvoice.soa.ServiceFactory;
import com.bazaarvoice.soa.zookeeper.ZooKeeperConfiguration;
import com.bazaarvoice.soa.zookeeper.ZooKeeperConnection;
import com.google.common.io.Closeables;
import com.netflix.curator.test.TestingServer;
import org.junit.Test;

import java.util.concurrent.ScheduledExecutorService;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ServicePoolBuilderTest {
    @Test(expected = NullPointerException.class)
    public void testNullHostDiscovery() {
        new ServicePoolBuilder<Service>().withHostDiscovery(null);
    }

    @Test(expected = NullPointerException.class)
    public void testNullZooKeeperConnection() {
        new ServicePoolBuilder<Service>().withZooKeeperHostDiscovery(null);
    }

    @Test(expected = NullPointerException.class)
    public void testNullServiceFactory() {
        new ServicePoolBuilder<Service>().withServiceFactory(null);
    }

    @Test(expected = NullPointerException.class)
    public void testNullHealthCheckExecutor() {
        new ServicePoolBuilder<Service>().withHealthCheckExecutor(null);
    }

    @SuppressWarnings("unchecked")
    @Test(expected = IllegalStateException.class)
    public void testBuildWithNoHostDiscoveryAndNoZooKeeperConnection() {
        ServiceFactory<Service> serviceFactory = (ServiceFactory<Service>) mock(ServiceFactory.class);
        ScheduledExecutorService healthCheckExecutor = mock(ScheduledExecutorService.class);
        new ServicePoolBuilder<Service>()
                .withServiceFactory(serviceFactory)
                .withHealthCheckExecutor(healthCheckExecutor)
                .build();
    }

    @Test(expected = NullPointerException.class)
    public void testBuildWithNoServiceFactory() {
        HostDiscovery hostDiscovery = mock(HostDiscovery.class);
        ScheduledExecutorService healthCheckExecutor = mock(ScheduledExecutorService.class);
        new ServicePoolBuilder<Service>()
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

        new ServicePoolBuilder<Service>()
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
            connection = new ZooKeeperConfiguration().setConnectString(zooKeeperServer.getConnectString()).connect();

            new ServicePoolBuilder<Service>()
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
    public void testBuildWithNoHealthCheckExecutor() {
        LoadBalanceAlgorithm loadBalanceAlgorithm = mock(LoadBalanceAlgorithm.class);
        HostDiscovery hostDiscovery = mock(HostDiscovery.class);

        ServiceFactory<Service> serviceFactory = (ServiceFactory<Service>) mock(ServiceFactory.class);
        when(serviceFactory.getLoadBalanceAlgorithm()).thenReturn(loadBalanceAlgorithm);

        new ServicePoolBuilder<Service>()
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

        new ServicePoolBuilder<Service>()
                .withServiceFactory(serviceFactory)
                .withHostDiscovery(hostDiscovery)
                .withHealthCheckExecutor(healthCheckExecutor)
                .build();
    }

    // A dummy interface for testing...
    private static interface Service {}
}
