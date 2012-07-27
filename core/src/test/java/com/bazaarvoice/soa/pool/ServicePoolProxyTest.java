package com.bazaarvoice.soa.pool;

import com.bazaarvoice.soa.HostDiscovery;
import com.bazaarvoice.soa.LoadBalanceAlgorithm;
import com.bazaarvoice.soa.RetryPolicy;
import com.bazaarvoice.soa.ServiceEndPoint;
import com.bazaarvoice.soa.ServiceFactory;
import com.bazaarvoice.soa.ServicePoolStatistics;
import com.google.common.base.Ticker;
import com.google.common.collect.ImmutableList;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ServicePoolProxyTest {
    private static final ServiceEndPoint FOO_ENDPOINT = mock(ServiceEndPoint.class);
    private static final Service FOO_SERVICE = mock(Service.class);
    private static final RetryPolicy NEVER_RETRY = mock(RetryPolicy.class);

    private ScheduledExecutorService _healthCheckExecutor;
    private ServicePool<Service> _pool;

    @SuppressWarnings("unchecked")
    @Before
    public void setup() {
        Ticker ticker = mock(Ticker.class);

        HostDiscovery hostDiscovery = mock(HostDiscovery.class);
        when(hostDiscovery.getHosts()).thenReturn(ImmutableList.of(FOO_ENDPOINT));

        LoadBalanceAlgorithm loadBalanceAlgorithm = mock(LoadBalanceAlgorithm.class);
        when(loadBalanceAlgorithm.choose(any(Iterable.class))).thenReturn(FOO_ENDPOINT);

        ServiceFactory<Service> serviceFactory = (ServiceFactory<Service>) mock(ServiceFactory.class);
        when(serviceFactory.create(FOO_ENDPOINT)).thenReturn(FOO_SERVICE);
        when(serviceFactory.getLoadBalanceAlgorithm(any(ServicePoolStatistics.class))).thenReturn(loadBalanceAlgorithm);

        _healthCheckExecutor = mock(ScheduledExecutorService.class);
        when(_healthCheckExecutor.scheduleAtFixedRate((Runnable) any(), anyLong(), anyLong(), (TimeUnit) any())).then(
                new Answer<ScheduledFuture<?>>() {
                    @Override
                    public ScheduledFuture<?> answer(InvocationOnMock invocation) throws Throwable {
                        return mock(ScheduledFuture.class);
                    }
                }
        );

        _pool = new ServicePool<Service>(ticker, hostDiscovery, serviceFactory,
                ServiceCachingPolicyBuilder.NO_CACHING, _healthCheckExecutor, true);
    }

    @After
    public void teardown() {
        _pool.close();
    }

    @Test
    public void testProxyDoesNotOverrideClose() throws IOException {
        // Because this proxy is created with shutdownPoolOnClose=false, the Service.close() method is passed
        // through to the underlying service implementation.
        Service service = ServicePoolProxy.create(Service.class, NEVER_RETRY, _pool, false);
        service.close();

        verify(FOO_SERVICE).close();
        verify(_healthCheckExecutor, never()).shutdownNow();
    }

    @Test
    public void testProxyDoesNotImplementCloseable() throws IOException {
        Service service = ServicePoolProxy.create(Service.class, NEVER_RETRY, _pool, false);

        assertFalse(service instanceof Closeable);
    }

    @Test
    public void testProxyImplementsCloseable() throws IOException {
        Service service = ServicePoolProxy.create(Service.class, NEVER_RETRY, _pool, true);

        assertTrue(service instanceof Closeable);
    }

    @Test
    public void testProxyCallsExecutorShutdownOnClose() throws IOException {
        Service service = ServicePoolProxy.create(Service.class, NEVER_RETRY, _pool, true);
        service.close();

        verify(_healthCheckExecutor).shutdownNow();
    }

    private static interface Service {
        void close();
    }
}
