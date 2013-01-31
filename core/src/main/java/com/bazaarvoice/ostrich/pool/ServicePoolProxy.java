package com.bazaarvoice.ostrich.pool;

import com.bazaarvoice.ostrich.PartitionContext;
import com.bazaarvoice.ostrich.RetryPolicy;
import com.bazaarvoice.ostrich.ServiceCallback;
import com.bazaarvoice.ostrich.ServicePool;
import com.bazaarvoice.ostrich.exceptions.ServiceException;
import com.google.common.base.Throwables;
import com.google.common.reflect.AbstractInvocationHandler;

import java.io.Closeable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

class ServicePoolProxy<S> extends AbstractInvocationHandler {
    private final Class<S> _serviceType;
    private final RetryPolicy _retryPolicy;
    private final ServicePool<S> _servicePool;
    private final PartitionContextSupplier _partitionContextSupplier;
    private final boolean _shutdownPoolOnClose;

    static <S> S create(Class<S> serviceType, RetryPolicy retryPolicy, ServicePool<S> pool,
                        PartitionContextSupplier partitionContextSupplier, boolean shutdownPoolOnClose) {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        Class<?>[] interfaces = shutdownPoolOnClose
                ? new Class<?>[] {serviceType, Closeable.class}
                : new Class<?>[] {serviceType};

        ServicePoolProxy<S> proxy = new ServicePoolProxy<S>(
                serviceType, retryPolicy, pool, partitionContextSupplier, shutdownPoolOnClose);
        return serviceType.cast(Proxy.newProxyInstance(loader, interfaces, proxy));
    }

    ServicePoolProxy(Class<S> serviceType, RetryPolicy retryPolicy, ServicePool<S> servicePool,
                     PartitionContextSupplier partitionContextSupplier, boolean shutdownPoolOnClose) {
        checkState(serviceType.isInterface(), "Proxy functionality is only available for interface service types.");

        _serviceType = checkNotNull(serviceType);
        _retryPolicy = checkNotNull(retryPolicy);
        _servicePool = checkNotNull(servicePool);
        _partitionContextSupplier = checkNotNull(partitionContextSupplier);
        _shutdownPoolOnClose = shutdownPoolOnClose;
    }

    /**
     * @return The service pool used by this proxy to execute service methods.
     */
    com.bazaarvoice.ostrich.ServicePool<S> getServicePool() {
        return _servicePool;
    }

    @Override
    protected Object handleInvocation(Object proxy, final Method method, final Object[] args) throws Throwable {
        // Special case for close() allows closing the entire pool by calling close() on the proxy.
        if (_shutdownPoolOnClose && args.length == 0 && method.getName().equals("close")) {
            _servicePool.close();
            return null;
        }

        PartitionContext partitionContext = _partitionContextSupplier.forCall(method, args);

        // Delegate the method through to a service provider in the pool.
        return _servicePool.execute(partitionContext, _retryPolicy, new ServiceCallback<S, Object>() {
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
