package com.bazaarvoice.ostrich.pool;

import com.bazaarvoice.ostrich.HostDiscovery;
import com.bazaarvoice.ostrich.HostDiscoverySource;
import com.bazaarvoice.ostrich.LoadBalanceAlgorithm;
import com.bazaarvoice.ostrich.RetryPolicy;
import com.bazaarvoice.ostrich.ServiceFactory;
import com.bazaarvoice.ostrich.loadbalance.RandomAlgorithm;
import com.bazaarvoice.ostrich.partition.IdentityPartitionFilter;
import com.bazaarvoice.ostrich.partition.PartitionFilter;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

public class ServicePoolBuilderTest {
    private ServiceFactory<Service> _serviceFactory;
    private ServiceCachingPolicy _cachingPolicy;
    private HostDiscovery _hostDiscovery;
    private ScheduledExecutorService _healthCheckExecutor;
    private ExecutorService _asyncExecutor;
    private PartitionFilter _partitionFilter;

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
        _partitionFilter = mock(PartitionFilter.class);
    }

    @Test(expected = NullPointerException.class)
    public void testNullHostDiscovery() {
        ServicePoolBuilder.create(Service.class).withHostDiscovery(null);
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
                .withPartitionFilter(_partitionFilter)
                .build();
    }

    @Test(expected = NullPointerException.class)
    public void testBuildWithNoServiceFactory() {
        ServicePoolBuilder.create(Service.class)
                .withCachingPolicy(_cachingPolicy)
                .withHostDiscovery(_hostDiscovery)
                .withHealthCheckExecutor(_healthCheckExecutor)
                .withPartitionFilter(_partitionFilter)
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
                .withPartitionFilter(_partitionFilter)
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
                .withPartitionFilter(_partitionFilter)
                .build();
    }

    @Test
    public void testBuildWithNullLoadBalanceAlgorithm() {
        ServicePoolBuilder.create(Service.class)
                .withServiceFactory(_serviceFactory)
                .withCachingPolicy(_cachingPolicy)
                .withHostDiscovery(_hostDiscovery)
                .withHealthCheckExecutor(_healthCheckExecutor)
                .withPartitionFilter(_partitionFilter)
                .build();
    }

    @Test
    public void testHostDiscoveryFromSourceCloses() throws Exception {
        HostDiscoverySource closingHostDiscoverySource = mock(HostDiscoverySource.class);
        HostDiscovery closedHostDiscovery = mock(HostDiscovery.class);
        HostDiscoverySource nonClosingHostDiscoverySource = mock(HostDiscoverySource.class);
        HostDiscovery unclosedHostDiscovery = mock(HostDiscovery.class);

        when(closingHostDiscoverySource.forService(anyString())).thenReturn(closedHostDiscovery);
        when(nonClosingHostDiscoverySource.forService(anyString())).thenReturn(unclosedHostDiscovery);

        ServicePool<Service> servicePool = (ServicePool<Service>) ServicePoolBuilder.create(Service.class)
                .withServiceFactory(_serviceFactory)
                .withCachingPolicy(_cachingPolicy)
                .withHostDiscoverySource(closingHostDiscoverySource)
                .withHostDiscoverySource(nonClosingHostDiscoverySource)
                .withPartitionFilter(_partitionFilter)
                .build();

        servicePool.close();
        verify(closedHostDiscovery).close();
        verify(unclosedHostDiscovery, never()).close();
    }

    @Test
    public void testHostDiscoveryDoesNotClose() throws Exception {
        HostDiscovery unclosedHostDiscovery = mock(HostDiscovery.class);

        ServicePool<Service> servicePool = (ServicePool<Service>) ServicePoolBuilder.create(Service.class)
                .withServiceFactory(_serviceFactory)
                .withCachingPolicy(_cachingPolicy)
                .withHostDiscovery(unclosedHostDiscovery)
                .withPartitionFilter(_partitionFilter)
                .build();

        servicePool.close();
        verify(unclosedHostDiscovery, never()).close();
    }

    @Test
    public void testHostDiscoverySourceOverride() {
        HostDiscovery overrideDiscovery = mock(HostDiscovery.class);

        HostDiscoverySource source = mock(HostDiscoverySource.class);
        when(source.forService(anyString())).thenReturn(overrideDiscovery);

        com.bazaarvoice.ostrich.pool.ServicePool<Service> pool = ServicePoolBuilder.create(Service.class)
                .withHostDiscoverySource(source)
                .withHostDiscovery(_hostDiscovery)
                .withServiceFactory(_serviceFactory)
                .withCachingPolicy(_cachingPolicy)
                .withPartitionFilter(_partitionFilter)
                .buildInternal();
        assertSame(overrideDiscovery, pool.getHostDiscovery());
    }

    @Test
    public void testHostDiscoverySourceFallThrough() {
        HostDiscoverySource source = mock(HostDiscoverySource.class);
        when(source.forService(anyString())).thenReturn(null);

        com.bazaarvoice.ostrich.pool.ServicePool<Service> pool = ServicePoolBuilder.create(Service.class)
                .withHostDiscoverySource(source)
                .withHostDiscovery(_hostDiscovery)
                .withServiceFactory(_serviceFactory)
                .withCachingPolicy(_cachingPolicy)
                .withPartitionFilter(_partitionFilter)
                .buildInternal();
        assertSame(_hostDiscovery, pool.getHostDiscovery());
    }

    @Test
    public void testBuildWithNoHealthCheckExecutor() {
        ServicePoolBuilder.create(Service.class)
                .withServiceFactory(_serviceFactory)
                .withCachingPolicy(_cachingPolicy)
                .withHostDiscovery(_hostDiscovery)
                .withPartitionFilter(_partitionFilter)
                .build();
    }

    @Test
    public void testBuildWithNoAsyncExecutor() {
        ServicePoolBuilder.create(Service.class)
                .withServiceFactory(_serviceFactory)
                .withCachingPolicy(_cachingPolicy)
                .withHostDiscovery(_hostDiscovery)
                .withHealthCheckExecutor(_healthCheckExecutor)
                .withPartitionFilter(_partitionFilter)
                .build();
    }

    @Test
    public void testBuildWithNoCachingPolicy() {
        ServicePoolBuilder.create(Service.class)
                .withServiceFactory(_serviceFactory)
                .withHostDiscovery(_hostDiscovery)
                .withHealthCheckExecutor(_healthCheckExecutor)
                .withPartitionFilter(_partitionFilter)
                .build();
    }

    @Test
    public void testBuildWithNoPartitionFilter() throws IOException {
        ServicePool<Service> service = (ServicePool<Service>) ServicePoolBuilder.create(Service.class)
                .withServiceFactory(_serviceFactory)
                .withCachingPolicy(_cachingPolicy)
                .withHostDiscovery(_hostDiscovery)
                .build();
        assertTrue(service.getPartitionFilter() instanceof IdentityPartitionFilter);
    }

    @Test
    public void testBuildWithPartitionFilter() throws IOException {
        ServicePool<Service> service = (ServicePool<Service>) ServicePoolBuilder.create(Service.class)
                .withServiceFactory(_serviceFactory)
                .withCachingPolicy(_cachingPolicy)
                .withHostDiscovery(_hostDiscovery)
                .withPartitionFilter(_partitionFilter)
                .build();
        assertSame(_partitionFilter, service.getPartitionFilter());
    }

    @Test
    public void testBuildWithNoLoadBalanceAlgorithm() throws IOException {
        ServicePool<Service> service = (ServicePool<Service>) ServicePoolBuilder.create(Service.class)
                .withServiceFactory(_serviceFactory)
                .withCachingPolicy(_cachingPolicy)
                .withHostDiscovery(_hostDiscovery)
                .withPartitionFilter(_partitionFilter)
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
                .withPartitionFilter(_partitionFilter)
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
                .withPartitionFilter(_partitionFilter)
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
                .withPartitionFilter(_partitionFilter)
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
                .withPartitionFilter(_partitionFilter)
                .buildAsync();
    }

    @Test
    public void testBuildProxy() throws IOException {
        Service service = ServicePoolBuilder.create(Service.class)
                .withServiceFactory(_serviceFactory)
                .withCachingPolicy(_cachingPolicy)
                .withHostDiscovery(_hostDiscovery)
                .withPartitionFilter(_partitionFilter)
                .buildProxy(mock(RetryPolicy.class));
        assertTrue(service instanceof Closeable);
    }

    @Test
    public void testBuildProxyWithAnnotations() {
        ServicePoolBuilder.create(Service.class)
                .withServiceFactory(_serviceFactory)
                .withCachingPolicy(_cachingPolicy)
                .withHostDiscovery(_hostDiscovery)
                .withPartitionContextAnnotations()
                .buildProxy(mock(RetryPolicy.class));
    }

    @Test(expected = NullPointerException.class)
    public void testBuildProxyWithAnnotationsFromNull() {
        ServicePoolBuilder.create(Service.class)
                .withServiceFactory(_serviceFactory)
                .withCachingPolicy(_cachingPolicy)
                .withHostDiscovery(_hostDiscovery)
                .withPartitionContextAnnotationsFrom(null)
                .buildProxy(mock(RetryPolicy.class));
    }

    @Test
    public void testBuildProxyWithAnnotationsFrom() {
        ServicePoolBuilder.create(Service.class)
                .withServiceFactory(_serviceFactory)
                .withCachingPolicy(_cachingPolicy)
                .withHostDiscovery(_hostDiscovery)
                .withPartitionContextAnnotationsFrom(ServiceChild.class)
                .buildProxy(mock(RetryPolicy.class));
    }

    @Test
    public void testServiceFactoryConfigure() {
        ServicePoolBuilder<Service> builder = ServicePoolBuilder.create(Service.class);
        builder.withServiceFactory(_serviceFactory);

        verify(_serviceFactory).configure(builder);
    }

    // A dummy interface for testing...
    private static interface Service {}

    private static class ServiceChild implements Service{}
}
