package com.bazaarvoice.ostrich.pool;

import com.bazaarvoice.ostrich.ServiceCallback;
import com.bazaarvoice.ostrich.ServiceEndPoint;
import com.bazaarvoice.ostrich.ServiceFactory;
import com.bazaarvoice.ostrich.ServicePoolStatistics;
import com.bazaarvoice.ostrich.exceptions.ServiceException;
import org.junit.Test;
import org.mockito.Matchers;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

public class PoolWithSingleThreadedCacheTest extends AbstractServicePoolTestingHarness {
    @Override
    protected ServiceCachingPolicy getServiceCachingPolicy() {
        return new ServiceCachingPolicyBuilder().build();
    }

    @Override
    @SuppressWarnings("unchecked")
    protected ServiceFactory<AbstractServicePoolTestingHarness.Service> getServiceFactoryMock() {
        return (ServiceFactory<Service>) mock(ServiceFactory.class);
    }

    @Test
    public void testStatsNumIdleCachedInstancesIncrementsAfterExecute() {
        // Make sure we only get FOO_ENDPOINT.
        reset(_loadBalanceAlgorithm);
        when(_loadBalanceAlgorithm.choose(Matchers.<Iterable<ServiceEndPoint>>any(), any(ServicePoolStatistics.class)))
                .thenReturn(FOO_ENDPOINT);

        final ServicePoolStatistics servicePoolStatistics = _pool.getServicePoolStatistics();

        int numIdleDuringExecute = _pool.execute(NEVER_RETRY, new ServiceCallback<Service, Integer>() {
            @Override
            public Integer call(Service service)
                    throws ServiceException {
                return servicePoolStatistics.getNumIdleCachedInstances(FOO_ENDPOINT);
            }
        });

        int numIdleAfterExecute = servicePoolStatistics.getNumIdleCachedInstances(FOO_ENDPOINT);

        assertEquals(numIdleDuringExecute + 1, numIdleAfterExecute);
    }

    @Test
    public void testStatsNumActiveInstancesDecrementsAfterExecute() {
        // Make sure we only get FOO_ENDPOINT.
        reset(_loadBalanceAlgorithm);
        when(_loadBalanceAlgorithm.choose(Matchers.<Iterable<ServiceEndPoint>>any(), any(ServicePoolStatistics.class)))
                .thenReturn(FOO_ENDPOINT);

        final ServicePoolStatistics servicePoolStatistics = _pool.getServicePoolStatistics();

        int numActiveDuringExecute = _pool.execute(NEVER_RETRY, new ServiceCallback<Service, Integer>() {
            @Override
            public Integer call(Service service)
                    throws ServiceException {
                return servicePoolStatistics.getNumActiveInstances(FOO_ENDPOINT);
            }
        });

        int numActiveAfterExecute = servicePoolStatistics.getNumActiveInstances(FOO_ENDPOINT);

        assertEquals(numActiveDuringExecute - 1, numActiveAfterExecute);
    }

    @SuppressWarnings ("unchecked")
    @Test
    public void testStatsNumIdleCachedInstancesDecrementsDuringExecute() {
        // Make sure we only get FOO_ENDPOINT.
        reset(_loadBalanceAlgorithm);
        when(_loadBalanceAlgorithm.choose(Matchers.<Iterable<ServiceEndPoint>>any(), any(ServicePoolStatistics.class)))
                .thenReturn(FOO_ENDPOINT);

        // Prime the cache.
        _pool.execute(NEVER_RETRY, mock(ServiceCallback.class));

        final ServicePoolStatistics servicePoolStatistics = _pool.getServicePoolStatistics();

        int numIdleInitially = servicePoolStatistics.getNumIdleCachedInstances(FOO_ENDPOINT);

        int numIdleDuringExecute = _pool.execute(NEVER_RETRY, new ServiceCallback<Service, Integer>() {
            @Override
            public Integer call(Service service)
                    throws ServiceException {
                return servicePoolStatistics.getNumIdleCachedInstances(FOO_ENDPOINT);
            }
        });

        assertEquals(numIdleInitially - 1, numIdleDuringExecute);
    }

}
