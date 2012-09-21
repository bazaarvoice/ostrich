package com.bazaarvoice.soa.pool;

import com.bazaarvoice.soa.HealthCheckResults;
import com.google.common.io.Closeables;

import java.lang.reflect.Proxy;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Utilities for working with service pool dynamic proxies created by
 * {@link ServicePoolBuilder#buildProxy(com.bazaarvoice.soa.RetryPolicy)}.
 */
public abstract class ServicePoolProxies {
    private ServicePoolProxies() {} // Prevent instantiation

    /**
     * Returns true if the specified object is a dynamic service proxy created by {@link ServicePoolBuilder#buildProxy}.
     * @param dynamicProxy An object that might be service dynamic proxy.
     * @return true if the specified object is a dynamic service proxy.
     */
    public static boolean isProxy(Object dynamicProxy) {
        return dynamicProxy instanceof Proxy && Proxy.getInvocationHandler(dynamicProxy) instanceof ServicePoolProxy;
    }

    /**
     * Closes the service pool associated with the specified dynamic service proxy.
     * @param dynamicProxy A service pool dynamic proxy created by {@link ServicePoolBuilder#buildProxy}.
     * @param <S> The service interface type.
     */
    public static <S> void close(S dynamicProxy) {
        // Use closeQuietly since ServicePool.close() doesn't throw IOException.
        Closeables.closeQuietly(getPool(dynamicProxy));
    }

    /**
     * Finds a healthy end point in the pool and provides the result of the health check that showed it to be healthy.
     * @see com.bazaarvoice.soa.ServicePool#checkForHealthyEndPoint
     * @param dynamicProxy A service pool dynamic proxy created by {@link ServicePoolBuilder#buildProxy}.
     * @param <S> The service interface type.
     * @return {@code HealthCheckResults} with the first healthy end point found, or a {@code HealthCheckResults}
     * containing all failed {@code HealthCheckResult}s encountered if no healthy end points exist.
     */
    public static <S> HealthCheckResults checkForHealthyEndPoint(S dynamicProxy) {
        return getPool(dynamicProxy).checkForHealthyEndPoint();
    }

    /**
     * Returns the {@link com.bazaarvoice.soa.ServicePool} used by the specified dynamic service proxy.  This can be
     * used for various reasons, including making a service call using non-standard retry parameters or getting access
     * to service pool statistics.
     * @param dynamicProxy A service pool dynamic proxy created by {@link ServicePoolBuilder#buildProxy}.
     * @param <S> The service interface type.
     * @return The {@link com.bazaarvoice.soa.ServicePool} used by the specified dynamic service proxy.
     */
    public static <S> com.bazaarvoice.soa.ServicePool<S> getPool(S dynamicProxy) {
        checkNotNull(dynamicProxy);
        checkArgument(isProxy(dynamicProxy));
        @SuppressWarnings("unchecked") ServicePoolProxy<S> poolProxy = (ServicePoolProxy<S>)
                Proxy.getInvocationHandler(dynamicProxy);
        return poolProxy.getServicePool();
    }
}
