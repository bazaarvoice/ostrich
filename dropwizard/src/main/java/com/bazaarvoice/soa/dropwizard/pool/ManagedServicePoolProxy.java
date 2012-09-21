package com.bazaarvoice.soa.dropwizard.pool;

import com.bazaarvoice.soa.RetryPolicy;
import com.bazaarvoice.soa.pool.ServicePoolBuilder;
import com.bazaarvoice.soa.pool.ServicePoolProxies;
import com.yammer.dropwizard.lifecycle.Managed;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Adapts the Dropwizard {@link Managed} interface for a dynamic service proxy created by
 * {@link ServicePoolBuilder#buildProxy(RetryPolicy)}.  This allows Dropwizard to shutdown service pools cleanly.
 * <p>
 * Here's how to use this class with an instance of a Dropwizard {@code Environment}:
 * <pre>
 *  Environment environment = ...;
 *  Service service = ServicePoolBuilder.create(Service.class)
 *     .withServiceFactory(...)
 *     .buildProxy(...);
 *  environment.manage(new ManagedServicePoolProxy(service));
 * </pre>
 */
public class ManagedServicePoolProxy implements Managed {
    private final Object _proxy;

    /**
     * Wraps the specified dynamic proxy with the Dropwizard {@link Managed} interface.
     * @param proxy A dynamic service proxy created by {@link ServicePoolBuilder#buildProxy(RetryPolicy)}.
     */
    public ManagedServicePoolProxy(Object proxy) {
        checkArgument(ServicePoolProxies.isProxy(proxy));
        _proxy = proxy;
    }

    @Override
    public void start() {
        // Nothing to do
    }

    @Override
    public void stop() {
        ServicePoolProxies.close(_proxy);
    }
}
