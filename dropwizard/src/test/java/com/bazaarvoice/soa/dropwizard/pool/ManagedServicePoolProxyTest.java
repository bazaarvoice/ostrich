package com.bazaarvoice.soa.dropwizard.pool;

import com.bazaarvoice.soa.ServicePool;
import com.bazaarvoice.soa.pool.ServicePoolProxyHelper;
import com.yammer.dropwizard.lifecycle.Managed;
import org.junit.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

public class ManagedServicePoolProxyTest {
    @Test(expected = IllegalArgumentException.class)
    public void testNull() {
        new ManagedServicePoolProxy(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNotProxy() {
        new ManagedServicePoolProxy(mock(Service.class));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testStart() throws Exception {
        ServicePool<Service> pool = mock(ServicePool.class);
        Service service = ServicePoolProxyHelper.createMock(Service.class, pool);
        Managed managed = new ManagedServicePoolProxy(service);

        managed.start();
        verifyZeroInteractions(pool);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testStop() throws Exception {
        ServicePool<Service> pool = mock(ServicePool.class);
        Service service = ServicePoolProxyHelper.createMock(Service.class, pool);
        Managed managed = new ManagedServicePoolProxy(service);

        managed.stop();
        verify(pool).close();
        verifyNoMoreInteractions(pool);
    }

    // A dummy interface for testing...
    private static interface Service {}
}
