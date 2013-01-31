package com.bazaarvoice.ostrich.pool;

import com.bazaarvoice.ostrich.HostDiscovery;
import com.bazaarvoice.ostrich.LoadBalanceAlgorithm;
import com.bazaarvoice.ostrich.PartitionContext;
import com.bazaarvoice.ostrich.RetryPolicy;
import com.bazaarvoice.ostrich.ServiceCallback;
import com.bazaarvoice.ostrich.ServiceEndPoint;
import com.bazaarvoice.ostrich.ServiceFactory;
import com.bazaarvoice.ostrich.ServicePoolStatistics;
import com.bazaarvoice.ostrich.exceptions.MaxRetriesException;
import com.bazaarvoice.ostrich.exceptions.ServiceException;
import com.bazaarvoice.ostrich.partition.PartitionFilter;
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

import java.io.IOException;
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
    private LoadBalanceAlgorithm _loadBalanceAlgorithm;
    private ScheduledExecutorService _healthCheckExecutor;
    private PartitionFilter _partitionFilter;
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

        _loadBalanceAlgorithm = mock(LoadBalanceAlgorithm.class);
        when(_loadBalanceAlgorithm.choose(any(Iterable.class), any(ServicePoolStatistics.class)))
                .thenReturn(FOO_ENDPOINT);

        _serviceFactory = (ServiceFactory<Service>) mock(ServiceFactory.class);
        when(_serviceFactory.getServiceName()).thenReturn(Service.class.getSimpleName());
        when(_serviceFactory.create(any(ServiceEndPoint.class))).then(new Answer<Service>() {
            @Override
            public Service answer(InvocationOnMock invocation) throws Throwable {
                return mock(Service.class);
            }
        });
        when(_serviceFactory.isRetriableException(any(Exception.class))).thenReturn(true);

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

        _partitionFilter = mock(PartitionFilter.class);
        when(_partitionFilter.filter(any(Iterable.class), any(PartitionContext.class)))
                .thenAnswer(new Answer<Iterable<ServiceEndPoint>>() {
                    @Override
                    public Iterable<ServiceEndPoint> answer(InvocationOnMock invocation) throws Throwable {
                        return (Iterable<ServiceEndPoint>) invocation.getArguments()[0];
                    }
                });
    }

    @After
    public void teardown() throws IOException {
        for (ServicePool<Service> pool : _pools) {
            pool.close();
        }

        _hostDiscovery.close();
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

        // Capture the end point listener that was registered with HostDiscovery
        ArgumentCaptor<HostDiscovery.EndPointListener> listener = ArgumentCaptor.forClass(
                HostDiscovery.EndPointListener.class);
        verify(_hostDiscovery).addListener(listener.capture());

        // Remove the end point from host discovery then add it back
        listener.getValue().onEndPointRemoved(FOO_ENDPOINT);
        listener.getValue().onEndPointAdded(FOO_ENDPOINT);

        assertNotSame(service, pool.execute(NEVER_RETRY, IDENTITY_CALLBACK));
    }

    @Test
    public void testEvictsCachedInstancesOnServiceException() {
        ServicePool<Service> pool = newPool(CACHE_ONE_INSTANCE_PER_ENDPOINT);
        Service service = pool.execute(NEVER_RETRY, IDENTITY_CALLBACK);

        // Set it up so that when we health check FOO, that it becomes healthy.
        when(_serviceFactory.isHealthy(FOO_ENDPOINT)).thenReturn(true);

        // Cause a service exception, the health check will happen inline and will mark the end point as valid again
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
    public void testDoesNotEvictCachedInstancesOnNonRetriableException() {
        when(_serviceFactory.isRetriableException(any(Exception.class))).thenReturn(false);

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


            // Capture the end point listener that was registered with HostDiscovery
            ArgumentCaptor<HostDiscovery.EndPointListener> listener = ArgumentCaptor.forClass(
                    HostDiscovery.EndPointListener.class);
            verify(_hostDiscovery).addListener(listener.capture());

            // Remove the end point from host discovery then add it back
            listener.getValue().onEndPointRemoved(FOO_ENDPOINT);
            listener.getValue().onEndPointAdded(FOO_ENDPOINT);

            // Let the initial callback terminate...
            canReturn.countDown();

            assertNotSame(serviceFuture.get(), pool.execute(NEVER_RETRY, IDENTITY_CALLBACK));
        } finally {
            executor.shutdown();
        }
    }

    private ServicePool<Service> newPool(ServiceCachingPolicy cachingPolicy) {
        ServicePool<Service> pool = new ServicePool<Service>(_ticker, _hostDiscovery, false, _serviceFactory,
                cachingPolicy, _partitionFilter, _loadBalanceAlgorithm, _healthCheckExecutor, true);
        _pools.add(pool);
        return pool;
    }

    private interface Service {}
}