package com.bazaarvoice.soa;

import com.bazaarvoice.soa.pool.ServicePoolBuilder;
import com.bazaarvoice.soa.pool.ServicePoolProxy;
import com.google.common.io.Closeables;

import java.lang.reflect.Proxy;

/**
 * Utilities for working with service pool dynamic proxies created by
 * {@link ServicePoolBuilder#buildProxy(RetryPolicy)}.
 */
public abstract class ServicePoolProxies {
    private ServicePoolProxies() {} // Prevent instantiation

    /**
     * Closes the service pool associated with the specified dynamic service proxy.
     * @param dynamicProxy A service pool dynamic proxy created by {@link ServicePoolBuilder#buildProxy(RetryPolicy)}.
     */
    public static void close(Object dynamicProxy) {
        // Use closeQuietly since ServicePool.close() doesn't throw IOException.
        Closeables.closeQuietly(getPool(dynamicProxy));
    }

    /**
     * Returns the {@link ServicePool} used by the specified dynamic service proxy.  This can be used for various
     * reasons, including making a service call using non-standard retry parameters or getting access to service pool
     * statistics.
     * @param dynamicProxy A service pool dynamic proxy created by {@link ServicePoolBuilder#buildProxy(RetryPolicy)}.
     * @param <S> The service interface type.
     * @return The {@link ServicePool} used by the specified dynamic service proxy.
     */
    public static <S> ServicePool<S> getPool(S dynamicProxy) {
        @SuppressWarnings("unchecked") ServicePoolProxy<S> poolProxy = (ServicePoolProxy<S>)
                Proxy.getInvocationHandler(dynamicProxy);
        return poolProxy.getServicePool();
    }
}
