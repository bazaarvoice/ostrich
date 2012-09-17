package com.bazaarvoice.soa.pool;

import com.bazaarvoice.soa.HealthCheckResults;
import com.bazaarvoice.soa.RetryPolicy;
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
        PartitionContextSupplier supplier = mock(PartitionContextSupplier.class);
        Service service = ServicePoolProxy.create(Service.class, mock(RetryPolicy.class), pool, supplier, true);

        ServicePoolProxies.close(service);

        verify(pool).close();
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testFindFirstHealthyEndPoint() {
        com.bazaarvoice.soa.ServicePool<Service> pool = mock(com.bazaarvoice.soa.ServicePool.class);
        PartitionContextSupplier supplier = mock(PartitionContextSupplier.class);
        HealthCheckResults results = mock(HealthCheckResults.class);
        when(pool.checkForHealthyEndPoint()).thenReturn(results);
        Service service = ServicePoolProxy.create(Service.class, mock(RetryPolicy.class), pool, supplier, true);

        assertSame(results, ServicePoolProxies.checkForHealthyEndPoint(service));
    }

    @Test(expected = NullPointerException.class)
    public void testNullProxy() {
        ServicePoolProxies.getPool(null);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testPoolGetter() {
        com.bazaarvoice.soa.ServicePool<Service> pool = mock(com.bazaarvoice.soa.ServicePool.class);
        PartitionContextSupplier supplier = mock(PartitionContextSupplier.class);
        Service service = ServicePoolProxy.create(Service.class, mock(RetryPolicy.class), pool, supplier, true);

        assertSame(pool, ServicePoolProxies.getPool(service));
    }

    // A dummy interface for testing...
    private static interface Service {}
}
