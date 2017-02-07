package com.bazaarvoice.soa.dropwizard.registry;

import com.bazaarvoice.soa.ServiceEndPoint;
import com.bazaarvoice.soa.ServiceRegistry;
import com.yammer.dropwizard.lifecycle.Managed;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Dropwizard {@link Managed} registration of a service end point.
 */
public class ManagedServiceRegistration implements Managed {
    private final ServiceRegistry _serviceRegistry;
    private final ServiceEndPoint _endPoint;

    public ManagedServiceRegistration(ServiceRegistry serviceRegistry, ServiceEndPoint endPoint) {
        _serviceRegistry = checkNotNull(serviceRegistry);
        _endPoint = checkNotNull(endPoint);
    }

    @Override
    public void start() throws Exception {
        _serviceRegistry.register(_endPoint);
    }

    @Override
    public void stop() throws Exception {
        _serviceRegistry.unregister(_endPoint);
    }
}
