package com.bazaarvoice.ostrich.pool;

import com.bazaarvoice.ostrich.HealthCheckResults;
import com.google.common.io.Closeables;

import java.io.IOException;
import java.lang.reflect.Proxy;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Utilities for working with service pool dynamic proxies created by
 * {@link ServicePoolBuilder#buildProxy(com.bazaarvoice.ostrich.RetryPolicy)}.
 */
public abstract class ServicePoolProxies {
    private ServicePoolProxies() {} // Prevent instantiation

    /**
     * Returns true if the specified object is a dynamic service proxy created by {@link ServicePoolBuilder#buildProxy}.
     *
     * @param dynamicProxy An object that might be service dynamic proxy.
     * @return true if the specified object is a dynamic service proxy.
     */
    public static boolean isProxy(Object dynamicProxy) {
        return dynamicProxy instanceof Proxy && Proxy.getInvocationHandler(dynamicProxy) instanceof ServicePoolProxy;
    }

    /**
     * Closes the service pool associated with the specified dynamic service proxy.
     *
     * @param <S>          The service interface type.
     * @param dynamicProxy A service pool dynamic proxy created by {@link ServicePoolBuilder#buildProxy}.
     */
    public static <S> void close(S dynamicProxy) {
        // Use closeQuietly since ServicePool.close() doesn't throw IOException.
        try {
            Closeables.close(getPool(dynamicProxy), true);
        } catch (IOException e) {
            throw new AssertionError();  // close swallows IOExceptions
        }
    }

    /**
     * Gets the number of valid end points that the provided service pool knows about.
     *
     * @param <S>          the type parameter
     * @param dynamicProxy the dynamic proxy
     * @return the num valid end points
     * @see ServicePool#getNumValidEndPoints() ServicePool#getNumValidEndPoints()
     */
    public static <S> int getNumValidEndPoints(S dynamicProxy) {
        return getPool(dynamicProxy).getNumValidEndPoints();
    }

    /**
     * Gets the number of bad end points that the provided service pool knows about.
     *
     * @param <S>          the type parameter
     * @param dynamicProxy the dynamic proxy
     * @return the num bad end points
     * @see ServicePool#getNumBadEndPoints() ServicePool#getNumBadEndPoints()
     */
    public static <S> int getNumBadEndPoints(S dynamicProxy) {
        return getPool(dynamicProxy).getNumBadEndPoints();
    }

    /**
     * Finds a healthy end point in the pool and provides the result of the health check that showed it to be healthy.
     *
     * @param <S>          The service interface type.
     * @param dynamicProxy A service pool dynamic proxy created by {@link ServicePoolBuilder#buildProxy}.
     * @return {@code HealthCheckResults} with the first healthy end point found, or a {@code HealthCheckResults} containing all failed {@code HealthCheckResult}s encountered if no healthy end points exist.
     * @see com.bazaarvoice.ostrich.ServicePool#checkForHealthyEndPoint com.bazaarvoice.ostrich.ServicePool#checkForHealthyEndPoint
     */
    public static <S> HealthCheckResults checkForHealthyEndPoint(S dynamicProxy) {
        return getPool(dynamicProxy).checkForHealthyEndPoint();
    }

    /**
     * Returns the {@link com.bazaarvoice.ostrich.ServicePool} used by the specified dynamic service proxy.  This can be
     * used for various reasons, including making a service call using non-standard retry parameters or getting access
     * to service pool statistics.
     *
     * @param <S>          The service interface type.
     * @param dynamicProxy A service pool dynamic proxy created by {@link ServicePoolBuilder#buildProxy}.
     * @return The {@link com.bazaarvoice.ostrich.ServicePool} used by the specified dynamic service proxy.
     */
    public static <S> com.bazaarvoice.ostrich.ServicePool<S> getPool(S dynamicProxy) {
        checkNotNull(dynamicProxy);
        checkArgument(isProxy(dynamicProxy));
        @SuppressWarnings("unchecked") ServicePoolProxy<S> poolProxy = (ServicePoolProxy<S>)
                Proxy.getInvocationHandler(dynamicProxy);
        return poolProxy.getServicePool();
    }
}
