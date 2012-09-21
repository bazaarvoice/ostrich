package com.bazaarvoice.soa.pool;

import com.bazaarvoice.soa.HealthCheckResults;
import com.bazaarvoice.soa.RetryPolicy;
import com.bazaarvoice.soa.ServicePool;
import com.google.common.reflect.Reflection;
import org.junit.Test;

import java.io.IOException;
import java.lang.reflect.InvocationHandler;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ServicePoolProxiesTest {
    @Test
    public void testIsNotProxyNull() throws IOException {
        assertFalse(ServicePoolProxies.isProxy(null));
    }

    @Test
    public void testIsNotProxyNonProxy() throws IOException {
        assertFalse(ServicePoolProxies.isProxy(new Object()));
    }

    @Test
    public void testIsNotProxyNonServicePoolProxy() throws IOException {
        Service service = Reflection.newProxy(Service.class, mock(InvocationHandler.class));

        assertFalse(ServicePoolProxies.isProxy(service));
    }

    @Test
    public void testIsProxy() throws IOException {
        @SuppressWarnings("unchecked")
        Service service = ServicePoolProxy.create(Service.class, mock(RetryPolicy.class), mock(ServicePool.class),
                mock(PartitionContextSupplier.class), true);

        assertTrue(ServicePoolProxies.isProxy(service));
    }

    @Test
    public void testClose() throws IOException {
        @SuppressWarnings("unchecked")
        ServicePool<Service> pool = mock(ServicePool.class);
        Service service = ServicePoolProxy.create(Service.class, mock(RetryPolicy.class), pool,
                mock(PartitionContextSupplier.class), true);

        ServicePoolProxies.close(service);

        verify(pool).close();
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testCheckForHealthyEndPoint() {
        ServicePool<Service> pool = mock(ServicePool.class);
        HealthCheckResults results = mock(HealthCheckResults.class);
        when(pool.checkForHealthyEndPoint()).thenReturn(results);
        Service service = ServicePoolProxy.create(Service.class, mock(RetryPolicy.class), pool,
                mock(PartitionContextSupplier.class), true);

        assertSame(results, ServicePoolProxies.checkForHealthyEndPoint(service));
    }

    @Test(expected = NullPointerException.class)
    public void testGetPoolNullProxy() {
        ServicePoolProxies.getPool(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGetPoolNonServicePoolProxy() {
        ServicePoolProxies.getPool(Reflection.newProxy(Service.class, mock(InvocationHandler.class)));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testGetPool() {
        ServicePool<Service> pool = mock(ServicePool.class);
        Service service = ServicePoolProxy.create(Service.class, mock(RetryPolicy.class), pool,
                mock(PartitionContextSupplier.class), true);

        assertSame(pool, ServicePoolProxies.getPool(service));
    }

    // A dummy interface for testing...
    private static interface Service {}
}
