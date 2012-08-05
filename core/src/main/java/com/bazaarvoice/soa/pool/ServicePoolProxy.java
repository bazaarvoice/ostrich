package com.bazaarvoice.soa.pool;

import com.bazaarvoice.soa.RetryPolicy;
import com.bazaarvoice.soa.ServiceCallback;
import com.bazaarvoice.soa.ServicePool;
import com.bazaarvoice.soa.exceptions.ServiceException;
import com.google.common.base.Throwables;
import com.google.common.reflect.AbstractInvocationHandler;

import java.io.Closeable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

public class ServicePoolProxy<S> extends AbstractInvocationHandler {
    private final Class<S> _serviceType;
    private final RetryPolicy _retryPolicy;
    private final ServicePool<S> _servicePool;
    private final boolean _shutdownPoolOnClose;

    static <S> S create(Class<S> serviceType, RetryPolicy retryPolicy,
                               ServicePool<S> pool, boolean shutdownPoolOnClose) {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        Class<?>[] interfaces = shutdownPoolOnClose
                ? new Class<?>[] {serviceType, Closeable.class}
                : new Class<?>[] {serviceType};

        ServicePoolProxy<S> proxy = new ServicePoolProxy<S>(serviceType, retryPolicy, pool, shutdownPoolOnClose);
        return serviceType.cast(Proxy.newProxyInstance(loader, interfaces, proxy));
    }

    ServicePoolProxy(Class<S> serviceType, RetryPolicy retryPolicy,
                     ServicePool<S> servicePool, boolean shutdownPoolOnClose) {
        checkState(serviceType.isInterface(), "Proxy functionality is only available for interface service types.");

        _serviceType = checkNotNull(serviceType);
        _retryPolicy = checkNotNull(retryPolicy);
        _servicePool = checkNotNull(servicePool);
        _shutdownPoolOnClose = shutdownPoolOnClose;
    }

    /**
     * Returns the service pool used by this proxy to execute service methods.
     */
    public ServicePool<S> getServicePool() {
        return _servicePool;
    }

    @Override
    protected Object handleInvocation(Object proxy, final Method method, final Object[] args) throws Throwable {
        // Special case for close() allows closing the entire pool by calling close() on the proxy.
        if (_shutdownPoolOnClose && args.length == 0 && method.getName().equals("close")) {
            _servicePool.close();
            return null;
        }

        // Delegate the method through to a service provider in the pool.
        return _servicePool.execute(_retryPolicy, new ServiceCallback<S, Object>() {
            @Override
            public Object call(S service) throws ServiceException {
                try {
                    return method.invoke(service, args);
                } catch (IllegalAccessException e) {
                    throw Throwables.propagate(e);
                } catch (InvocationTargetException e) {
                    throw Throwables.propagate(e.getTargetException());
                }
            }
        });
    }

    @Override
    public String toString() {
        return "ServicePoolProxy[" + _serviceType.getName() + "]";
    }
}
