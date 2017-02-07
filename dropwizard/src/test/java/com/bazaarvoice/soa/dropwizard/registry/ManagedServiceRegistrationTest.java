package com.bazaarvoice.soa.dropwizard.registry;

import com.bazaarvoice.soa.ServiceEndPoint;
import com.bazaarvoice.soa.ServiceRegistry;
import org.junit.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class ManagedServiceRegistrationTest {
    @Test (expected = NullPointerException.class)
    public void testNullRegistry() {
        new ManagedServiceRegistration(null, mock(ServiceEndPoint.class));
    }

    @Test (expected = NullPointerException.class)
    public void testNullEndPoint() {
        new ManagedServiceRegistration(mock(ServiceRegistry.class), null);
    }

    @Test
    public void testStart() throws Exception {
        ServiceRegistry registry = mock(ServiceRegistry.class);
        ServiceEndPoint endPoint = mock(ServiceEndPoint.class);
        ManagedServiceRegistration managedServiceRegistration = new ManagedServiceRegistration(registry, endPoint);

        managedServiceRegistration.start();
        verify(registry).register(endPoint);
    }

    @Test
    public void testStop() throws Exception {
        ServiceRegistry registry = mock(ServiceRegistry.class);
        ServiceEndPoint endPoint = mock(ServiceEndPoint.class);
        ManagedServiceRegistration managedServiceRegistration = new ManagedServiceRegistration(registry, endPoint);

        managedServiceRegistration.stop();
        verify(registry).unregister(endPoint);
    }
}
