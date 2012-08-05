package com.bazaarvoice.soa.pool;

import com.bazaarvoice.soa.RetryPolicy;
import com.bazaarvoice.soa.ServicePoolProxies;
import org.junit.Test;

import java.io.IOException;

import static junit.framework.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class ServicePoolProxiesTest {
    @Test
    public void testClose() throws IOException {
        @SuppressWarnings("unchecked") com.bazaarvoice.soa.ServicePool<Service> pool = mock(com.bazaarvoice.soa.ServicePool.class);
        Service service = ServicePoolProxy.create(Service.class, mock(RetryPolicy.class), pool, true);

        ServicePoolProxies.close(service);

        verify(pool).close();
    }

    @Test
    public void testPoolGetter() {
        @SuppressWarnings("unchecked") com.bazaarvoice.soa.ServicePool<Service> pool = mock(com.bazaarvoice.soa.ServicePool.class);
        Service service = ServicePoolProxy.create(Service.class, mock(RetryPolicy.class), pool, true);

        assertTrue(ServicePoolProxies.getPool(service) == pool);
    }

    // A dummy interface for testing...
    private static interface Service {}
}
