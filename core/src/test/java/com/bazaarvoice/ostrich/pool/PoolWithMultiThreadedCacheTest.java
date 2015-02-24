package com.bazaarvoice.ostrich.pool;

import com.bazaarvoice.ostrich.ServiceFactory;
import com.bazaarvoice.ostrich.ThreadSafeServiceFactory;

import static org.mockito.Mockito.mock;

public class PoolWithMultiThreadedCacheTest extends AbstractServicePoolTestingHarness {
    @Override
    protected ServiceCachingPolicy getServiceCachingPolicy() {
        // eviction delay is set to zero to invalidate an evicted instance immediately.
        // this allows us to validate the call to ServiceFactory#destroy()
        return ServiceCachingPolicyBuilder.newMultiThreadedClientPolicy(0);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected ServiceFactory<Service> getServiceFactoryMock() {
        // Multi threaded service cache requires the service factory to
        // implement ThreadSafeServiceFactory (which extends ServiceFactory)
        return (ServiceFactory<Service>) mock(ThreadSafeServiceFactory.class);
    }
}
