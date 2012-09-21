package com.bazaarvoice.soa.pool;

import com.google.common.reflect.Reflection;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Test helper that exposes package-private methods on {@link ServicePoolProxy}.
 */
public class ServicePoolProxyHelper {
    public static <S> S createMock(Class<S> serviceType, com.bazaarvoice.soa.ServicePool<S> pool) {
        @SuppressWarnings("unchecked")
        ServicePoolProxy<S> servicePoolProxy = mock(ServicePoolProxy.class);
        when(servicePoolProxy.getServicePool()).thenReturn(pool);
        return Reflection.newProxy(serviceType, servicePoolProxy);
    }
}
