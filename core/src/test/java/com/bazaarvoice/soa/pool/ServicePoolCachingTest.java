package com.bazaarvoice.soa.pool;

import com.bazaarvoice.soa.HostDiscovery;
import com.bazaarvoice.soa.LoadBalanceAlgorithm;
import com.bazaarvoice.soa.RetryPolicy;
import com.bazaarvoice.soa.ServiceCallback;
import com.bazaarvoice.soa.ServiceEndPoint;
import com.bazaarvoice.soa.ServiceFactory;
import com.bazaarvoice.soa.exceptions.MaxRetriesException;
import com.bazaarvoice.soa.exceptions.ServiceException;
import com.google.common.base.Throwables;
import com.google.common.base.Ticker;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.Futures;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ServicePoolCachingTest {
    private static final ServiceEndPoint FOO_ENDPOINT = mock(ServiceEndPoint.class);
    private static final RetryPolicy NEVER_RETRY = mock(RetryPolicy.class);

    private static final ServiceCachingPolicy CACHE_ONE_INSTANCE_PER_ENDPOINT = new ServiceCachingPolicyBuilder()
            .withMaxNumServiceInstancesPerEndPoint(1)
            .build();

    private static final ServiceCallback<Service, Service> IDENTITY_CALLBACK = new ServiceCallback<Service, Service>() {
        @Override
        public Service call(Service service) throws ServiceException {
            return service;
        }
    };

    private Ticker _ticker;
    private HostDiscovery _hostDiscovery;
    private ServiceFactory<Service> _serviceFactory;
    private ScheduledExecutorService _healthCheckExecutor;
    private List<ServicePool<Service>> _pools = Lists.newArrayList();

    @SuppressWarnings("unchecked")
    @Before
    public void setup() {
        //
        // This setup method takes the approach of building a reasonably useful ServicePool using mocks that can then be
        // customized by individual test methods to add whatever functionality they need to (or ignored completely).
        //

        _ticker = mock(Ticker.class);

        _hostDiscovery = mock(HostDiscovery.class);
        when(_hostDiscovery.getHosts()).thenReturn(ImmutableList.of(FOO_ENDPOINT));
        when(_hostDiscovery.contains(FOO_ENDPOINT)).thenReturn(true);

        LoadBalanceAlgorithm loadBalanceAlgorithm = mock(LoadBalanceAlgorithm.class);
        when(loadBalanceAlgorithm.choose(any(Iterable.class))).thenReturn(FOO_ENDPOINT);

        _serviceFactory = (ServiceFactory<Service>) mock(ServiceFactory.class);
        when(_serviceFactory.create(any(ServiceEndPoint.class))).then(new Answer<Service>() {
            @Override
            public Service answer(InvocationOnMock invocation) throws Throwable {
                return mock(Service.class);
            }
        });
        when(_serviceFactory.getLoadBalanceAlgorithm()).thenReturn(loadBalanceAlgorithm);

        _healthCheckExecutor = mock(ScheduledExecutorService.class);
        when(_healthCheckExecutor.submit(any(Runnable.class))).then(new Answer<Future<?>>() {
            @Override
            public Future<?> answer(InvocationOnMock invocation) throws Throwable {
                // Execute the runnable on this thread...
                Runnable runnable = (Runnable) invocation.getArguments()[0];
                runnable.run();

                // The task is already complete, so the future should return null as per the ScheduledExecutorService
                // contract.
                return Futures.immediateFuture(null);
            }
        });
        when(_healthCheckExecutor.scheduleAtFixedRate((Runnable) any(), anyLong(), anyLong(), (TimeUnit) any())).then(
                new Answer<ScheduledFuture<?>>() {
                    @Override
                    public ScheduledFuture<?> answer(InvocationOnMock invocation) throws Throwable {
                        return mock(ScheduledFuture.class);
                    }
                }
        );
    }

    @After
    public void teardown() {
        for (ServicePool<Service> pool : _pools) {
            pool.close();
        }
    }

    @Test
    public void testServiceInstanceIsCached() {
        ServicePool<Service> pool = newPool(CACHE_ONE_INSTANCE_PER_ENDPOINT);
        Service service = pool.execute(NEVER_RETRY, IDENTITY_CALLBACK);

        assertSame(service, pool.execute(NEVER_RETRY, IDENTITY_CALLBACK));
    }

    @Test
    public void testEvictsAllCachedInstancesWhenHostDiscoveryRemovesEndPoint() {
        ServicePool<Service> pool = newPool(CACHE_ONE_INSTANCE_PER_ENDPOINT);
        Service service = pool.execute(NEVER_RETRY, IDENTITY_CALLBACK);

        // Capture the endpoint listener that was registered with HostDiscovery
        ArgumentCaptor<HostDiscovery.EndpointListener> listener = ArgumentCaptor.forClass(
                HostDiscovery.EndpointListener.class);
        verify(_hostDiscovery).addListener(listener.capture());

        // Remove the end point from host discovery then add it back
        listener.getValue().onEndpointRemoved(FOO_ENDPOINT);
        listener.getValue().onEndpointAdded(FOO_ENDPOINT);

        assertNotSame(service, pool.execute(NEVER_RETRY, IDENTITY_CALLBACK));
    }

    @Test
    public void testEvictsCachedInstancesOnServiceException() {
        ServicePool<Service> pool = newPool(CACHE_ONE_INSTANCE_PER_ENDPOINT);
        Service service = pool.execute(NEVER_RETRY, IDENTITY_CALLBACK);

        // Set it up so that when we health check FOO, that it becomes healthy.
        when(_serviceFactory.isHealthy(FOO_ENDPOINT)).thenReturn(true);

        // Cause a service exception, the health check will happen inline and will mark the endpoint as valid again
        try {
            pool.execute(NEVER_RETRY, new ServiceCallback<Service, Void>() {
                @Override
                public Void call(Service service) throws ServiceException {
                    throw new ServiceException();
                }
            });
            fail();
        } catch (MaxRetriesException expected) {
            // Expected
        }

        assertNotSame(service, pool.execute(NEVER_RETRY, IDENTITY_CALLBACK));
    }

    @Test
    public void testDoesNotEvictCachedInstancesOnNonServiceException() {
        ServicePool<Service> pool = newPool(CACHE_ONE_INSTANCE_PER_ENDPOINT);
        Service service = pool.execute(NEVER_RETRY, IDENTITY_CALLBACK);

        // Cause an exception, this won't trigger a health check since it's not a ServiceException.
        try {
            pool.execute(NEVER_RETRY, new ServiceCallback<Service, Void>() {
                @Override
                public Void call(Service service) throws ServiceException {
                    throw new NullPointerException();
                }
            });
            fail();
        } catch (NullPointerException expected) {
            // Expected
        }

        assertSame(service, pool.execute(NEVER_RETRY, IDENTITY_CALLBACK));
    }

    @Test
    public void testWithServiceExceptionRemoving() throws ExecutionException, InterruptedException {
        final ServicePool<Service> pool = newPool(CACHE_ONE_INSTANCE_PER_ENDPOINT);
        final CountDownLatch canReturn = new CountDownLatch(1);

        // Set it up so that when we health check FOO, that it becomes healthy.
        when(_serviceFactory.isHealthy(FOO_ENDPOINT)).thenReturn(true);

        final CountDownLatch callableStarted = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<Service> serviceFuture = executor.submit(new Callable<Service>() {
                @Override
                public Service call() throws Exception {
                    return pool.execute(NEVER_RETRY, new ServiceCallback<Service, Service>() {
                        @Override
                        public Service call(Service service) throws ServiceException {
                            callableStarted.countDown();

                            // Block until we're allowed to return
                            try {
                                canReturn.await(10, TimeUnit.SECONDS);
                            } catch (InterruptedException e) {
                                throw Throwables.propagate(e);
                            }

                            return service;
                        }
                    });
                }
            });

            // Wait until the callable has definitely started and allocated a service instance...
            assertTrue(callableStarted.await(10, TimeUnit.SECONDS));

            // Throw an exception so that the end point is marked as bad and removed from the cache
            try {
                pool.execute(NEVER_RETRY, new ServiceCallback<Service, Void>() {
                    @Override
                    public Void call(Service service) throws ServiceException {
                        throw new ServiceException();
                    }
                });
                fail();
            } catch (MaxRetriesException expected) {
                // expected exception
            }

            // Let the initial callback terminate...
            canReturn.countDown();

            assertNotSame(serviceFuture.get(), pool.execute(NEVER_RETRY, IDENTITY_CALLBACK));
        } finally {
            executor.shutdown();
        }
    }

    @Test
    public void testWithHostDiscoveryRemoving() throws ExecutionException, InterruptedException {
        final ServicePool<Service> pool = newPool(CACHE_ONE_INSTANCE_PER_ENDPOINT);
        final CountDownLatch canReturn = new CountDownLatch(1);

        // Set it up so that when we health check FOO, that it becomes healthy.
        when(_serviceFactory.isHealthy(FOO_ENDPOINT)).thenReturn(true);

        final CountDownLatch callableStarted = new CountDownLatch(1);

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<Service> serviceFuture = executor.submit(new Callable<Service>() {
                @Override
                public Service call() throws Exception {
                    return pool.execute(NEVER_RETRY, new ServiceCallback<Service, Service>() {
                        @Override
                        public Service call(Service service) throws ServiceException {
                            callableStarted.countDown();

                            // Block until we're allowed to return
                            try {
                                canReturn.await(10, TimeUnit.SECONDS);
                            } catch (InterruptedException e) {
                                throw Throwables.propagate(e);
                            }

                            return service;
                        }
                    });
                }
            });

            // Wait until the callable has definitely started and allocated a service instance...
            assertTrue(callableStarted.await(10, TimeUnit.SECONDS));


            // Capture the endpoint listener that was registered with HostDiscovery
            ArgumentCaptor<HostDiscovery.EndpointListener> listener = ArgumentCaptor.forClass(
                    HostDiscovery.EndpointListener.class);
            verify(_hostDiscovery).addListener(listener.capture());

            // Remove the end point from host discovery then add it back
            listener.getValue().onEndpointRemoved(FOO_ENDPOINT);
            listener.getValue().onEndpointAdded(FOO_ENDPOINT);

            // Let the initial callback terminate...
            canReturn.countDown();

            assertNotSame(serviceFuture.get(), pool.execute(NEVER_RETRY, IDENTITY_CALLBACK));
        } finally {
            executor.shutdown();
        }
    }

    private ServicePool<Service> newPool(ServiceCachingPolicy cachingPolicy) {
        ServicePool<Service> pool = new ServicePool<Service>(Service.class, _ticker, _hostDiscovery, _serviceFactory,
                cachingPolicy, _healthCheckExecutor, true);
        _pools.add(pool);
        return pool;
    }

    private interface Service {}
}