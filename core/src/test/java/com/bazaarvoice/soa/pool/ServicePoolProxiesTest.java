package com.bazaarvoice.soa.pool;

import com.bazaarvoice.soa.*;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ServicePoolProxiesTest {
    @Test
    public void testClose() throws IOException {
        @SuppressWarnings("unchecked")
        com.bazaarvoice.soa.ServicePool<Service> pool = mock(com.bazaarvoice.soa.ServicePool.class);
        Service service = ServicePoolProxy.create(Service.class, mock(RetryPolicy.class), pool, true);

        ServicePoolProxies.close(service);

        verify(pool).close();
    }

    @Test
    public void testFindFirstHealthyEndPoint() {
        @SuppressWarnings("unchecked")
        com.bazaarvoice.soa.ServicePool<Service> pool = mock(com.bazaarvoice.soa.ServicePool.class);
        HealthCheckResults results = mock(HealthCheckResults.class);
        when(pool.checkForHealthyEndPoint()).thenReturn(results);
        Service service = ServicePoolProxy.create(Service.class, mock(RetryPolicy.class), pool, true);

        assertSame(results, ServicePoolProxies.checkForHealthyEndPoint(service));
    }

    @Test(expected = NullPointerException.class)
    public void testNullProxy() {
        ServicePoolProxies.getPool(null);
    }

    @Test
    public void testPoolGetter() {
        @SuppressWarnings("unchecked") com.bazaarvoice.soa.ServicePool<Service> pool = mock(com.bazaarvoice.soa.ServicePool.class);
        Service service = ServicePoolProxy.create(Service.class, mock(RetryPolicy.class), pool, true);

        assertSame(pool, ServicePoolProxies.getPool(service));
    }

    // A dummy interface for testing...
    private static interface Service {}
}
