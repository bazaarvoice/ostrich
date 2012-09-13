package com.bazaarvoice.soa.pool;

import com.bazaarvoice.soa.HostDiscovery;
import com.bazaarvoice.soa.HostDiscoverySource;
import com.bazaarvoice.soa.LoadBalanceAlgorithm;
import com.bazaarvoice.soa.RetryPolicy;
import com.bazaarvoice.soa.ServiceFactory;
import com.bazaarvoice.soa.loadbalance.RandomAlgorithm;
import com.bazaarvoice.zookeeper.ZooKeeperConfiguration;
import com.bazaarvoice.zookeeper.ZooKeeperConnection;
import com.google.common.io.Closeables;
import com.netflix.curator.test.TestingServer;
import org.junit.Before;
import org.junit.Test;

import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

public class ServicePoolBuilderTest {
    private ServiceFactory<Service> _serviceFactory;
    private ServiceCachingPolicy _cachingPolicy;
    private HostDiscovery _hostDiscovery;
    private ScheduledExecutorService _healthCheckExecutor;
    private ExecutorService _asyncExecutor;

    @SuppressWarnings("unchecked")
    @Before
    public void setup() {
        _serviceFactory = (ServiceFactory<Service>) mock(ServiceFactory.class);
        when(_serviceFactory.getServiceName()).thenReturn(Service.class.getSimpleName());

        _cachingPolicy = mock(ServiceCachingPolicy.class);
        when(_cachingPolicy.getCacheExhaustionAction()).thenReturn(ServiceCachingPolicy.ExhaustionAction.GROW);

        _hostDiscovery = mock(HostDiscovery.class);
        _healthCheckExecutor = mock(ScheduledExecutorService.class);
        _asyncExecutor = mock(ExecutorService.class);
    }

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
    public void testNullCachingPolicy() {
        ServicePoolBuilder.create(Service.class).withCachingPolicy(null);
    }

    @Test(expected = NullPointerException.class)
    public void testNullHealthCheckExecutor() {
        ServicePoolBuilder.create(Service.class).withHealthCheckExecutor(null);
    }

    @Test(expected = NullPointerException.class)
    public void testNullAsyncExecutor() {
        ServicePoolBuilder.create(Service.class).withAsyncExecutor(null);
    }

    @Test(expected = IllegalStateException.class)
    public void testBuildWithNoHostDiscoveryAndNoZooKeeperConnection() {
        ServicePoolBuilder.create(Service.class)
                .withServiceFactory(_serviceFactory)
                .withCachingPolicy(_cachingPolicy)
                .withHealthCheckExecutor(_healthCheckExecutor)
                .build();
    }

    @Test(expected = NullPointerException.class)
    public void testBuildWithNoServiceFactory() {
        ServicePoolBuilder.create(Service.class)
                .withCachingPolicy(_cachingPolicy)
                .withHostDiscovery(_hostDiscovery)
                .withHealthCheckExecutor(_healthCheckExecutor)
                .build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testBuildWithNullServiceName() {
        when(_serviceFactory.getServiceName()).thenReturn(null);

        ServicePoolBuilder.create(Service.class)
                .withServiceFactory(_serviceFactory)
                .withCachingPolicy(_cachingPolicy)
                .withHostDiscovery(_hostDiscovery)
                .withHealthCheckExecutor(_healthCheckExecutor)
                .build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testBuildWithEmptyServiceName() {
        when(_serviceFactory.getServiceName()).thenReturn("");

        ServicePoolBuilder.create(Service.class)
                .withServiceFactory(_serviceFactory)
                .withCachingPolicy(_cachingPolicy)
                .withHostDiscovery(_hostDiscovery)
                .withHealthCheckExecutor(_healthCheckExecutor)
                .build();
    }

    @Test
    public void testBuildWithNullLoadBalanceAlgorithm() {
        ServicePoolBuilder.create(Service.class)
                .withServiceFactory(_serviceFactory)
                .withCachingPolicy(_cachingPolicy)
                .withHostDiscovery(_hostDiscovery)
                .withHealthCheckExecutor(_healthCheckExecutor)
                .build();
    }

    @Test
    public void testBuildWithZooKeeperConnection() throws Exception {
        TestingServer zooKeeperServer = new TestingServer();
        ZooKeeperConnection connection = null;
        try {
            connection = new ZooKeeperConfiguration().withConnectString(zooKeeperServer.getConnectString()).connect();

            ServicePoolBuilder.create(Service.class)
                    .withServiceFactory(_serviceFactory)
                    .withCachingPolicy(_cachingPolicy)
                    .withZooKeeperHostDiscovery(connection)
                    .build();
        } finally {
            Closeables.closeQuietly(connection);
            Closeables.closeQuietly(zooKeeperServer);
        }
    }

    @Test
    public void testHostDiscoverySourceOverride() {
        HostDiscovery overrideDiscovery = mock(HostDiscovery.class);

        HostDiscoverySource source = mock(HostDiscoverySource.class);
        when(source.forService(anyString())).thenReturn(overrideDiscovery);

        com.bazaarvoice.soa.pool.ServicePool<Service> pool = ServicePoolBuilder.create(Service.class)
                .withHostDiscoverySource(source)
                .withHostDiscovery(_hostDiscovery)
                .withServiceFactory(_serviceFactory)
                .withCachingPolicy(_cachingPolicy)
                .buildInternal();
        assertSame(overrideDiscovery, pool.getHostDiscovery());
    }

    @Test
    public void testHostDiscoverySourceFallThrough() {
        HostDiscoverySource source = mock(HostDiscoverySource.class);
        when(source.forService(anyString())).thenReturn(null);

        com.bazaarvoice.soa.pool.ServicePool<Service> pool = ServicePoolBuilder.create(Service.class)
                .withHostDiscoverySource(source)
                .withHostDiscovery(_hostDiscovery)
                .withServiceFactory(_serviceFactory)
                .withCachingPolicy(_cachingPolicy)
                .buildInternal();
        assertSame(_hostDiscovery, pool.getHostDiscovery());
    }

    @Test
    public void testBuildWithNoHealthCheckExecutor() {
        ServicePoolBuilder.create(Service.class)
                .withServiceFactory(_serviceFactory)
                .withCachingPolicy(_cachingPolicy)
                .withHostDiscovery(_hostDiscovery)
                .build();
    }

    @Test
    public void testBuildWithNoAsyncExecutor() {
        ServicePoolBuilder.create(Service.class)
                .withServiceFactory(_serviceFactory)
                .withCachingPolicy(_cachingPolicy)
                .withHostDiscovery(_hostDiscovery)
                .withHealthCheckExecutor(_healthCheckExecutor)
                .build();
    }

    @Test
    public void testBuildWithNoCachingPolicy() {
        ServicePoolBuilder.create(Service.class)
                .withServiceFactory(_serviceFactory)
                .withHostDiscovery(_hostDiscovery)
                .withHealthCheckExecutor(_healthCheckExecutor)
                .build();
    }

    @Test
    public void testBuildWithNoLoadBalanceAlgorithm() throws IOException {
        ServicePool<Service> service = (ServicePool<Service>) ServicePoolBuilder.create(Service.class)
                .withServiceFactory(_serviceFactory)
                .withCachingPolicy(_cachingPolicy)
                .withHostDiscovery(_hostDiscovery)
                .build();
        assertTrue(service.getLoadBalanceAlgorithm() instanceof RandomAlgorithm);
    }

    @Test
    public void testBuildWithLoadBalanceAlgorithm() throws IOException {
        LoadBalanceAlgorithm loadBalanceAlgorithm = mock(LoadBalanceAlgorithm.class);
        ServicePool<Service> service = (ServicePool<Service>) ServicePoolBuilder.create(Service.class)
                .withServiceFactory(_serviceFactory)
                .withCachingPolicy(_cachingPolicy)
                .withLoadBalanceAlgorithm(loadBalanceAlgorithm)
                .withHostDiscovery(_hostDiscovery)
                .build();
        assertEquals(loadBalanceAlgorithm, service.getLoadBalanceAlgorithm());
    }

    @Test
    public void testBuildWithAsyncExecutor() {
        ServicePoolBuilder.create(Service.class)
                .withServiceFactory(_serviceFactory)
                .withCachingPolicy(_cachingPolicy)
                .withHostDiscovery(_hostDiscovery)
                .withHealthCheckExecutor(_healthCheckExecutor)
                .withAsyncExecutor(_asyncExecutor)
                .build();

        verifyZeroInteractions(_asyncExecutor);
    }

    @Test
    public void testBuildAsyncWithNoAsyncExecutor() {
        ServicePoolBuilder.create(Service.class)
                .withServiceFactory(_serviceFactory)
                .withCachingPolicy(_cachingPolicy)
                .withHostDiscovery(_hostDiscovery)
                .withHealthCheckExecutor(_healthCheckExecutor)
                .buildAsync();
    }

    @Test
    public void testBuildAsync() {
        ServicePoolBuilder.create(Service.class)
                .withServiceFactory(_serviceFactory)
                .withCachingPolicy(_cachingPolicy)
                .withHostDiscovery(_hostDiscovery)
                .withHealthCheckExecutor(_healthCheckExecutor)
                .withAsyncExecutor(_asyncExecutor)
                .buildAsync();
    }

    @Test
    public void testBuildProxy() throws IOException {
        Service service = ServicePoolBuilder.create(Service.class)
                .withServiceFactory(_serviceFactory)
                .withCachingPolicy(_cachingPolicy)
                .withHostDiscovery(_hostDiscovery)
                .buildProxy(mock(RetryPolicy.class));
        assertTrue(service instanceof Closeable);
    }

    @Test
    public void testServiceFactoryConfigure() {
        ServicePoolBuilder<Service> builder = ServicePoolBuilder.create(Service.class);
        builder.withServiceFactory(_serviceFactory);

        verify(_serviceFactory).configure(builder);
    }

    // A dummy interface for testing...
    private static interface Service {}
}
