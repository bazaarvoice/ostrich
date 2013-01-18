package com.bazaarvoice.soa.pool;

import com.bazaarvoice.soa.HealthCheckResults;
import com.bazaarvoice.soa.HostDiscovery;
import com.bazaarvoice.soa.LoadBalanceAlgorithm;
import com.bazaarvoice.soa.PartitionContext;
import com.bazaarvoice.soa.RetryPolicy;
import com.bazaarvoice.soa.ServiceCallback;
import com.bazaarvoice.soa.ServiceEndPoint;
import com.bazaarvoice.soa.ServiceFactory;
import com.bazaarvoice.soa.ServicePoolStatistics;
import com.bazaarvoice.soa.exceptions.MaxRetriesException;
import com.bazaarvoice.soa.exceptions.NoAvailableHostsException;
import com.bazaarvoice.soa.exceptions.NoSuitableHostsException;
import com.bazaarvoice.soa.exceptions.OnlyBadHostsException;
import com.bazaarvoice.soa.exceptions.ServiceException;
import com.bazaarvoice.soa.partition.PartitionFilter;
import com.google.common.base.Ticker;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.Futures;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Matchers;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.io.IOException;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

public class ServicePoolTest {
    private static final ServiceEndPoint FOO_ENDPOINT = mock(ServiceEndPoint.class);
    private static final ServiceEndPoint BAR_ENDPOINT = mock(ServiceEndPoint.class);
    private static final ServiceEndPoint BAZ_ENDPOINT = mock(ServiceEndPoint.class);
    private static final Service FOO_SERVICE = mock(Service.class);
    private static final Service BAR_SERVICE = mock(Service.class);
    private static final Service BAZ_SERVICE = mock(Service.class);
    private static final RetryPolicy NEVER_RETRY = mock(RetryPolicy.class);
    private static final ServiceCachingPolicy UNLIMITED_CACHING = new ServiceCachingPolicyBuilder().build();

    private Ticker _ticker;
    private HostDiscovery _hostDiscovery;
    private PartitionFilter _partitionFilter;
    private LoadBalanceAlgorithm _loadBalanceAlgorithm;
    private ServiceFactory<Service> _serviceFactory;
    private ScheduledExecutorService _healthCheckExecutor;
    private ScheduledFuture<?> _healthCheckScheduledFuture;
    private ServicePool<Service> _pool;

    @SuppressWarnings("unchecked")
    @Before
    public void setup() {
        //
        // This setup method takes the approach of building a reasonably useful ServicePool using mocks that can then be
        // customized by individual test methods to add whatever functionality they need to (or ignored completely).
        //

        _ticker = mock(Ticker.class);

        _hostDiscovery = mock(HostDiscovery.class);
        when(_hostDiscovery.getHosts()).thenReturn(ImmutableList.of(FOO_ENDPOINT, BAR_ENDPOINT, BAZ_ENDPOINT));

        _partitionFilter = mock(PartitionFilter.class);
        when(_partitionFilter.filter(any(Iterable.class), any(PartitionContext.class)))
                .thenAnswer(new Answer<Object>() {
                    @Override
                    public Iterable<ServiceEndPoint> answer(InvocationOnMock invocation) throws Throwable {
                        return (Iterable<ServiceEndPoint>) invocation.getArguments()[0];
                    }
                });

        _loadBalanceAlgorithm = mock(LoadBalanceAlgorithm.class);
        when(_loadBalanceAlgorithm.choose(any(Iterable.class), any(ServicePoolStatistics.class)))
                .thenAnswer(new Answer<ServiceEndPoint>() {
            @Override
            public ServiceEndPoint answer(InvocationOnMock invocation) throws Throwable {
                // Always choose the first end point.  This is probably fine since most tests will have just a single
                // end point available anyways.
                Iterable<ServiceEndPoint> endPoints = (Iterable<ServiceEndPoint>) invocation.getArguments()[0];
                return endPoints.iterator().next();
            }
        });

        _serviceFactory = (ServiceFactory<Service>) mock(ServiceFactory.class);
        when(_serviceFactory.getServiceName()).thenReturn(Service.class.getSimpleName());
        when(_serviceFactory.create(FOO_ENDPOINT)).thenReturn(FOO_SERVICE);
        when(_serviceFactory.create(BAR_ENDPOINT)).thenReturn(BAR_SERVICE);
        when(_serviceFactory.create(BAZ_ENDPOINT)).thenReturn(BAZ_SERVICE);
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

        _healthCheckScheduledFuture = mock(ScheduledFuture.class);
        when(_healthCheckExecutor.scheduleAtFixedRate((Runnable) any(), anyLong(), anyLong(), (TimeUnit) any())).then(
                new Answer<ScheduledFuture<?>>() {
                    @Override
                    public ScheduledFuture<?> answer(InvocationOnMock invocation) throws Throwable {
                        return _healthCheckScheduledFuture;
                    }
                }
        );

        _pool = new ServicePool<Service>(_ticker, _hostDiscovery, false, _serviceFactory, UNLIMITED_CACHING,
                _partitionFilter, _loadBalanceAlgorithm, _healthCheckExecutor, true);
    }

    @After
    public void teardown() throws IOException {
        _pool.close();

        _hostDiscovery.close();
    }

    @Test
    public void testCallInvokedWithCorrectService() {
        Service expectedService = mock(Service.class);

        // Wire our expected service into the system
        when(_serviceFactory.create(FOO_ENDPOINT)).thenReturn(expectedService);

        // Don't leak service end points in real code!!!  This is just a test case.
        Service actualService = _pool.execute(NEVER_RETRY, new ServiceCallback<Service, Service>() {
            @Override
            public Service call(Service s) {
                return s;
            }
        });

        assertSame(expectedService, actualService);
    }

    @Test(expected = NoAvailableHostsException.class)
    public void testThrowsNoAvailableHostsExceptionWhenNoEndPointsAvailable() {
        // Host discovery sees no end points...
        when(_hostDiscovery.getHosts()).thenReturn(ImmutableList.<ServiceEndPoint>of());
        _pool.execute(NEVER_RETRY, null);
    }

    @Test(expected = OnlyBadHostsException.class)
    public void testThrowsOnlyBadHostsExceptionWhenOnlyBadEndPointsAvailable() {
        // Exhaust all of the end points...
        int numEndPointsAvailable = Iterables.size(_hostDiscovery.getHosts());
        for (int i = 0; i < numEndPointsAvailable; i++) {
            try {
                _pool.execute(NEVER_RETRY, new ServiceCallback<Service, Void>() {
                    @Override
                    public Void call(Service service) throws ServiceException {
                        throw new ServiceException();
                    }
                });
                fail();  // should have propagated service exception
            } catch (MaxRetriesException e) {
                // Expected
            }
        }

        // This should trigger a service exception because there are no more available end points.
        _pool.execute(NEVER_RETRY, null);
    }

    @Test(expected = NoSuitableHostsException.class)
    public void testNullPartitionFilter() {
        reset(_partitionFilter);
        when(_partitionFilter.filter(Matchers.<Iterable<ServiceEndPoint>>any(), any(PartitionContext.class)))
                .thenReturn(null);

        boolean called = _pool.execute(NEVER_RETRY, new ServiceCallback<Service, Boolean>() {
            @Override
            public Boolean call(Service service) throws ServiceException {
                return true;
            }
        });
        assertFalse(called);
    }

    @Test(expected = NoSuitableHostsException.class)
    public void testEmptyPartition() {
        reset(_partitionFilter);
        when(_partitionFilter.filter(Matchers.<Iterable<ServiceEndPoint>>any(), any(PartitionContext.class)))
                .thenReturn(ImmutableList.<ServiceEndPoint>of());

        boolean called = _pool.execute(NEVER_RETRY, new ServiceCallback<Service, Boolean>() {
            @Override
            public Boolean call(Service service) throws ServiceException {
                return true;
            }
        });
        assertFalse(called);
    }


    @SuppressWarnings("unchecked")
    @Test
    public void testPartitionFilter() {
        reset(_partitionFilter);
        when(_partitionFilter.filter(Matchers.<Iterable<ServiceEndPoint>>any(), any(PartitionContext.class)))
                .thenReturn(ImmutableList.of(BAR_ENDPOINT));
        PartitionContext context = mock(PartitionContext.class);

        _pool.execute(context, NEVER_RETRY, new ServiceCallback<Service, Boolean>() {
            @Override
            public Boolean call(Service service) throws ServiceException {
                return true;
            }
        });

        // Verify the PartitionFilter was called with the correct arguments
        ArgumentCaptor<Iterable> filterEndPoints = ArgumentCaptor.forClass(Iterable.class);
        verify(_partitionFilter).filter(filterEndPoints.capture(), eq(context));
        assertEquals(ImmutableList.of(FOO_ENDPOINT, BAR_ENDPOINT, BAZ_ENDPOINT),
                ImmutableList.copyOf(filterEndPoints.getValue()));

        // Verify that the result of the PartitionFilter was passed on as input to the LoadBalanceAlgorithm
        ArgumentCaptor<Iterable> balanceEndPoints = ArgumentCaptor.forClass(Iterable.class);
        verify(_loadBalanceAlgorithm).choose(balanceEndPoints.capture(), any(ServicePoolStatistics.class));
        assertEquals(ImmutableList.of(BAR_ENDPOINT), ImmutableList.copyOf(balanceEndPoints.getValue()));
    }

    @Test(expected = NoSuitableHostsException.class)
    public void testThrowsNoSuitableHostsExceptionWhenLoadBalancerReturnsNull() {
        // Reset the load balance algorithm's setup and make it always return null.
        reset(_loadBalanceAlgorithm);
        when(_loadBalanceAlgorithm.choose(Matchers.<Iterable<ServiceEndPoint>>any(), any(ServicePoolStatistics.class)))
                .thenReturn(null);

        boolean called = _pool.execute(NEVER_RETRY, new ServiceCallback<Service, Boolean>() {
            @Override
            public Boolean call(Service service) throws ServiceException {
                return true;
            }
        });
        assertFalse(called);
    }

    @Test
    public void testDoesNotRetryOnCallbackSuccess() {
        RetryPolicy retry = mock(RetryPolicy.class);
        _pool.execute(retry, new ServiceCallback<Service, Void>() {
            @Override
            public Void call(Service service) {
                return null;
            }
        });

        // Should have never called the retry strategy for anything.  This might change in the future if we implement
        // circuit breakers.
        verifyZeroInteractions(retry);
    }

    @Test
    public void testAttemptsToRetryOnRetriableException() {
        when(_serviceFactory.isRetriableException(any(Exception.class))).thenReturn(true);
        RetryPolicy retry = mock(RetryPolicy.class);

        when(retry.allowRetry(anyInt(), anyLong())).thenReturn(false);

        try {
            _pool.execute(retry, new ServiceCallback<Service, Void>() {
                @Override
                public Void call(Service service) {
                    throw new ServiceException();
                }
            });

            fail();
        } catch (MaxRetriesException expected) {
            // We expect a service exception to happen since we're not going to be allowed to retry at all.
            // Make sure we asked the retry strategy if it was okay to retry one time (it said no).
            verify(retry).allowRetry(eq(1), anyLong());
        }
    }

    @Test
    public void testDoesNotAttemptToRetryOnNonRetriableException() {
        when(_serviceFactory.isRetriableException(any(Exception.class))).thenReturn(false);
        RetryPolicy retry = mock(RetryPolicy.class);

        try {
            _pool.execute(retry, new ServiceCallback<Service, Void>() {
                @Override
                public Void call(Service service) throws ServiceException {
                    throw new NullPointerException();
                }
            });

            fail();
        } catch (NullPointerException expected) {
            verifyZeroInteractions(retry);
        }
    }

    @Test
    public void testKeepsRetryingUntilRetryPolicyReturnsFalse() {
        RetryPolicy retry = mock(RetryPolicy.class);
        when(retry.allowRetry(anyInt(), anyLong())).thenReturn(true, true, false);

        try {
            _pool.execute(retry, new ServiceCallback<Service, Void>() {
                @Override
                public Void call(Service service) throws ServiceException {
                    throw new ServiceException();
                }
            });

            fail();
        } catch (MaxRetriesException expected) {
            // Make sure we tried 3 times.
            verify(retry).allowRetry(eq(3), anyLong());
        }
    }

    @Test
    public void testRetriesWithDifferentServiceEndPoints() {
        RetryPolicy retry = mock(RetryPolicy.class);
        when(retry.allowRetry(anyInt(), anyLong())).thenReturn(true, true, false);

        // Each end point has a specific service that it's supposed to return.  Remember each service we've seen so
        // that we can make sure we saw the correct ones.
        final Set<Service> seenServices = Sets.newHashSet();

        try {
            _pool.execute(retry, new ServiceCallback<Service, Void>() {
                @Override
                public Void call(Service service) throws ServiceException {
                    seenServices.add(service);
                    throw new ServiceException();
                }
            });

            fail();
        } catch (MaxRetriesException expected) {
            assertEquals(Sets.newHashSet(FOO_SERVICE, BAR_SERVICE, BAZ_SERVICE), seenServices);
        }
    }

    @Test
    public void testSubmitsHealthCheckOnRetriableException() {
        try {
            _pool.execute(NEVER_RETRY, new ServiceCallback<Service, Void>() {
                @Override
                public Void call(Service service) throws ServiceException {
                    throw new ServiceException();
                }
            });

            fail();
        } catch (MaxRetriesException expected) {
            // Expected exception
        }

        // Make sure we added a health check.
        verify(_healthCheckExecutor).submit(any(com.bazaarvoice.soa.pool.ServicePool.HealthCheck.class));
    }

    @Test
    public void testDoesNotSubmitHealthCheckOnNonRetriableException() {
        when(_serviceFactory.isRetriableException(any(Exception.class))).thenReturn(false);
        try {
            _pool.execute(NEVER_RETRY, new ServiceCallback<Service, Void>() {
                @Override
                public Void call(Service service) throws ServiceException {
                    throw new NullPointerException();
                }
            });

            fail();
        } catch (NullPointerException expected) {
            // Expected exception
        }

        // Make sure we didn't add a health check.
        verify(_healthCheckExecutor, never()).submit(any(com.bazaarvoice.soa.pool.ServicePool.HealthCheck.class));
    }

    @Test
    public void testSchedulesPeriodicHealthCheckUponCreation() {
        // The pool was already created so the health check executor should have been used already.

        verify(_healthCheckExecutor).scheduleAtFixedRate(
                any(com.bazaarvoice.soa.pool.ServicePool.BatchHealthChecks.class),
                eq(com.bazaarvoice.soa.pool.ServicePool.HEALTH_CHECK_POLL_INTERVAL_IN_SECONDS),
                eq(com.bazaarvoice.soa.pool.ServicePool.HEALTH_CHECK_POLL_INTERVAL_IN_SECONDS),
                eq(TimeUnit.SECONDS));
    }

    @Test
    public void testStatsPassedToLoadBalancer() {
        _pool.execute(NEVER_RETRY, new ServiceCallback<Service, Void>() {
            @Override
            public Void call(Service service) throws ServiceException {
                return null;
            }
        });

        verify(_loadBalanceAlgorithm).choose(Matchers.<Iterable<ServiceEndPoint>>any(),
                same(_pool.getServicePoolStatistics()));
    }

    @Test
    public void testStatsNumActiveInstancesIncrementsDuringExecute() {
        // Make sure we only get FOO_ENDPOINT.
        reset(_loadBalanceAlgorithm);
        when(_loadBalanceAlgorithm.choose(Matchers.<Iterable<ServiceEndPoint>>any(), any(ServicePoolStatistics.class)))
                .thenReturn(FOO_ENDPOINT);

        final ServicePoolStatistics servicePoolStatistics = _pool.getServicePoolStatistics();

        int numActiveInitially = servicePoolStatistics.getNumActiveInstances(FOO_ENDPOINT);

        int numActiveDuringExecute = _pool.execute(NEVER_RETRY, new ServiceCallback<Service, Integer>() {
            @Override
            public Integer call(Service service) throws ServiceException {
                return servicePoolStatistics.getNumActiveInstances(FOO_ENDPOINT);
            }
        });

        assertEquals(numActiveInitially + 1, numActiveDuringExecute);
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
            public Integer call(Service service) throws ServiceException {
                return servicePoolStatistics.getNumActiveInstances(FOO_ENDPOINT);
            }
        });

        int numActiveAfterExecute = servicePoolStatistics.getNumActiveInstances(FOO_ENDPOINT);

        assertEquals(numActiveDuringExecute - 1, numActiveAfterExecute);
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
            public Integer call(Service service) throws ServiceException {
                return servicePoolStatistics.getNumIdleCachedInstances(FOO_ENDPOINT);
            }
        });

        int numIdleAfterExecute = servicePoolStatistics.getNumIdleCachedInstances(FOO_ENDPOINT);

        assertEquals(numIdleDuringExecute + 1, numIdleAfterExecute);
    }

    @SuppressWarnings("unchecked")
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
            public Integer call(Service service) throws ServiceException {
                return servicePoolStatistics.getNumIdleCachedInstances(FOO_ENDPOINT);
            }
        });

        assertEquals(numIdleInitially - 1, numIdleDuringExecute);
    }

    @Test
    public void testCheckForHealthyEndPointWhenEmpty() {
        when(_hostDiscovery.getHosts()).thenReturn(Collections.<ServiceEndPoint>emptySet());

        assertTrue(Iterables.isEmpty(_pool.checkForHealthyEndPoint().getAllResults()));
    }

    @Test
    public void testCheckForHealthyEndPointWhenHealthy() {
        when(_serviceFactory.isHealthy(any(ServiceEndPoint.class))).thenReturn(true);

        HealthCheckResults results = _pool.checkForHealthyEndPoint();

        assertTrue(results.hasHealthyResult());
        assertTrue(Iterables.isEmpty(results.getUnhealthyResults()));
    }

    @Test
    public void testCheckForHealthyEndPointWhenUnhealthy() {
        when(_serviceFactory.isHealthy(any(ServiceEndPoint.class))).thenReturn(false);

        HealthCheckResults results = _pool.checkForHealthyEndPoint();

        assertFalse(results.hasHealthyResult());
        assertFalse(Iterables.isEmpty(results.getUnhealthyResults()));
    }

    @Test
    public void testCheckForHealthyEndPointRetriesWhenUnhealthy() {
        when(_serviceFactory.isHealthy(any(ServiceEndPoint.class))).thenReturn(false, true);

        HealthCheckResults results = _pool.checkForHealthyEndPoint();

        assertTrue(results.hasHealthyResult());
        assertFalse(Iterables.isEmpty(results.getUnhealthyResults()));
    }

    @Test
    public void testCheckForHealthyEndPointMarksEndPointBad() {
        when(_serviceFactory.isHealthy(any(ServiceEndPoint.class))).thenReturn(false);

        _pool.checkForHealthyEndPoint();

        assertTrue(_pool.getBadEndPoints().containsAll(Sets.newHashSet(_pool.getAllEndPoints())));
    }

    @Test
    public void testCheckForHealthyEndPointNotBeholdenToLoadBalancer() {
        reset(_loadBalanceAlgorithm);
        when(_loadBalanceAlgorithm.choose(Matchers.<Iterable<ServiceEndPoint>>any(), any(ServicePoolStatistics.class)))
                .thenReturn(null);

        assertFalse(Iterables.isEmpty(_pool.checkForHealthyEndPoint().getAllResults()));
    }

    @Test
    public void testCheckForHealthyEndPointRetriableException() {
        when(_serviceFactory.isRetriableException(any(Exception.class))).thenReturn(true);
        when(_serviceFactory.isHealthy(any(ServiceEndPoint.class))).thenThrow(new RuntimeException()).thenReturn(true);

        assertTrue(_pool.checkForHealthyEndPoint().hasHealthyResult());
    }

    @Test
    public void testCheckForHealthyEndPointNonRetriableException() {
        when(_serviceFactory.isRetriableException(any(Exception.class))).thenReturn(false);
        when(_serviceFactory.isHealthy(any(ServiceEndPoint.class))).thenThrow(new RuntimeException()).thenReturn(true);

        assertFalse(_pool.checkForHealthyEndPoint().hasHealthyResult());
    }

    @Test
    public void testCancelsPeriodicHealthCheckAfterClose() {
        // Future shouldn't be used until after we close...
        verifyZeroInteractions(_healthCheckScheduledFuture);

        _pool.close();
        verify(_healthCheckScheduledFuture).cancel(anyBoolean());
    }

    @Test
    public void testCallsHealthCheckAfterRetriableException() throws InterruptedException {
        final AtomicBoolean healthCheckCalled = new AtomicBoolean(false);
        when(_serviceFactory.isHealthy(any(ServiceEndPoint.class))).then(new Answer<Boolean>() {
            @Override
            public Boolean answer(InvocationOnMock invocation) throws Throwable {
                healthCheckCalled.set(true);
                return false;
            }
        });

        try {
            _pool.execute(NEVER_RETRY, new ServiceCallback<Service, Void>() {
                @Override
                public Void call(Service service) throws ServiceException {
                    throw new ServiceException();
                }
            });

            fail();
        } catch (MaxRetriesException expected) {
            // Expected exception
        }

        // A health check should have been called...we use the equivalent of a same thread executor, so we know that
        // it's been called already.  No need to wait.
        assertTrue(healthCheckCalled.get());
    }

    @Test
    public void testDoesNotCallHealthCheckAfterNonRetriableException() throws InterruptedException {
        when(_serviceFactory.isRetriableException(any(Exception.class))).thenReturn(false);
        try {
            _pool.execute(NEVER_RETRY, new ServiceCallback<Service, Void>() {
                @Override
                public Void call(Service service) throws ServiceException {
                    throw new NullPointerException();
                }
            });

            fail();
        } catch (NullPointerException expected) {
            // Expected exception
        }

        // Make sure we never tried to call any health checks
        verify(_serviceFactory, never()).isHealthy(any(ServiceEndPoint.class));
    }

    @Test
    public void testAllowsEndPointToBeUsedAgainAfterSuccessfulHealthCheck() {
        // Only allow BAZ to have a valid health check -- we know based on the load balance strategy that this
        // will be the last failed end point
        when(_serviceFactory.isHealthy(eq(BAZ_ENDPOINT))).thenReturn(true);

        // Exhaust all of the end points...
        int numEndPointsAvailable = Iterables.size(_hostDiscovery.getHosts());
        for (int i = 0; i < numEndPointsAvailable; i++) {
            try {
                _pool.execute(NEVER_RETRY, new ServiceCallback<Service, Void>() {
                    @Override
                    public Void call(Service service) throws ServiceException {
                        throw new ServiceException();
                    }
                });
                fail();  // should have propagated service exception
            } catch (MaxRetriesException e) {
                // Expected
            }
        }

        // BAZ should still be healthy, so this shouldn't throw an exception.
        Service usedService = _pool.execute(NEVER_RETRY, new ServiceCallback<Service, Service>() {
            @Override
            public Service call(Service service) throws ServiceException {
                return service;
            }
        });
        assertSame(BAZ_SERVICE, usedService);
    }

    @Test
    public void testBatchHealthCheckAllowsEndPointToBeUsedAgainAfterSuccessfulHealthCheck() {
        // Exhaust all of the end points...
        int numEndPointsAvailable = Iterables.size(_hostDiscovery.getHosts());
        for (int i = 0; i < numEndPointsAvailable; i++) {
            try {
                _pool.execute(NEVER_RETRY, new ServiceCallback<Service, Void>() {
                    @Override
                    public Void call(Service service) throws ServiceException {
                        throw new ServiceException();
                    }
                });
                fail();  // should have propagated service exception
            } catch (MaxRetriesException e) {
                // Expected
            }
        }

        // Set it up so that when we health check FOO, that it becomes healthy.
        when(_serviceFactory.isHealthy(FOO_ENDPOINT)).thenReturn(true);

        // Capture the BatchHealthChecks runnable that was registered with the executor so that we can execute it.
        ArgumentCaptor<Runnable> check = ArgumentCaptor.forClass(Runnable.class);
        verify(_healthCheckExecutor).scheduleAtFixedRate(check.capture(), anyLong(), anyLong(), any(TimeUnit.class));

        // Execute the background health checks, this should make FOO healthy again.
        check.getValue().run();

        // FOO should be healthy so we shouldn't get an exception.
        Service usedService = _pool.execute(NEVER_RETRY, new ServiceCallback<Service, Service>() {
            @Override
            public Service call(Service service) throws ServiceException {
                return service;
            }
        });
        assertSame(FOO_SERVICE, usedService);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testBadEndPointIsNoLongerHealthCheckedAfterHostDiscoveryRemovesIt() {
        // Redefine the end points that HostDiscovery knows about to be only FOO
        when(_hostDiscovery.getHosts()).thenReturn(ImmutableList.of(FOO_ENDPOINT));

        // Make it so that FOO is considered bad...
        try {
            _pool.execute(NEVER_RETRY, new ServiceCallback<Service, Void>() {
                @Override
                public Void call(Service service) throws ServiceException {
                    throw new ServiceException();
                }
            });
            fail();  // should have propagated service exception
        } catch (MaxRetriesException e) {
            // Expected
        }

        // At this point the health check for FOO would have been executed since it just failed.  We're going to
        // reset the serviceFactory mock at this point so that we forget that that has happened.  Once we reset it,
        // it's not going to be good for much, but the rest of this test fortunately doesn't interact with it.
        reset(_serviceFactory);

        // Capture the end point listener that was registered with HostDiscovery
        ArgumentCaptor<HostDiscovery.EndPointListener> listener = ArgumentCaptor.forClass(
                HostDiscovery.EndPointListener.class);
        verify(_hostDiscovery).addListener(listener.capture());

        // Now, have HostDiscovery fire an event saying that FOO has been removed
        listener.getValue().onEndPointRemoved(FOO_ENDPOINT);

        // Capture the BatchHealthChecks runnable that was registered with the executor so that we can execute it.
        ArgumentCaptor<Runnable> check = ArgumentCaptor.forClass(Runnable.class);
        verify(_healthCheckExecutor).scheduleAtFixedRate(check.capture(), anyLong(), anyLong(), any(TimeUnit.class));

        // Execute the background health checks, this shouldn't check FOO at all
        check.getValue().run();

        verify(_serviceFactory, never()).isHealthy(eq(FOO_ENDPOINT));
    }

    @Test
    public void testBadEndPointDisappearingFromHostDiscoveryDuringCallback() {
        // Redefine the end points that HostDiscovery knows about to be only FOO
        when(_hostDiscovery.getHosts()).thenReturn(ImmutableList.of(FOO_ENDPOINT));

        // Capture the end point listener that was registered with HostDiscovery
        final ArgumentCaptor<HostDiscovery.EndPointListener> listener = ArgumentCaptor.forClass(
                HostDiscovery.EndPointListener.class);
        verify(_hostDiscovery).addListener(listener.capture());

        try {
            _pool.execute(NEVER_RETRY, new ServiceCallback<Service, Void>() {
                @Override
                public Void call(Service service) throws ServiceException {
                    // Have HostDiscovery tell the ServicePool that FOO is gone.
                    listener.getValue().onEndPointRemoved(FOO_ENDPOINT);

                    // Now fail this request, this shouldn't result with any bad end points
                    throw new ServiceException();
                }
            });
            fail();  // should have propagated service exception
        } catch (MaxRetriesException e) {
            // Expected
        }

        // At this point the bad end points list should be empty
        assertTrue(_pool.getBadEndPoints().isEmpty());
    }

    @Test
    public void testIsHealthyHandlesExceptions() {
        when(_serviceFactory.isHealthy(FOO_ENDPOINT)).thenThrow(new RuntimeException());

        // Even though an exception was thrown we shouldn't see it, instead false should be returned from checkHealth
        assertFalse(_pool.checkHealth(FOO_ENDPOINT).isHealthy());
    }

    @Test
    public void testCloseMultipleTimes() {
        _pool.close();
        _pool.close();
    }

    @Test
    public void testDoesNotShutdownHealthCheckExecutorOnClose() {
        ServicePool<Service> pool = new ServicePool<Service>(_ticker, _hostDiscovery, false, _serviceFactory,
                ServiceCachingPolicyBuilder.NO_CACHING, _partitionFilter, _loadBalanceAlgorithm, _healthCheckExecutor,
                false);
        pool.close();

        verify(_healthCheckExecutor, never()).shutdown();
        verify(_healthCheckExecutor, never()).shutdownNow();
    }

    @Test
    public void testDoesShutdownHealthCheckExecutorOnClose() {
        ServicePool<Service> pool = new ServicePool<Service>(_ticker, _hostDiscovery, false, _serviceFactory,
                ServiceCachingPolicyBuilder.NO_CACHING, _partitionFilter, _loadBalanceAlgorithm, _healthCheckExecutor,
                true);
        pool.close();

        verify(_healthCheckExecutor, never()).shutdown();
        verify(_healthCheckExecutor).shutdownNow();
    }

    @Test
    public void testInterruptsHealthCheckOnClose() throws InterruptedException {
        // Make it so that when we health check FOO that we block until an interrupted exception occurs
        final CountDownLatch inHealthCheckLatch = new CountDownLatch(1);
        final CountDownLatch interruptedLatch = new CountDownLatch(1);
        when(_serviceFactory.isHealthy(FOO_ENDPOINT)).thenAnswer(new Answer<Boolean>() {
            @Override
            public Boolean answer(InvocationOnMock invocation) {
                inHealthCheckLatch.countDown();

                synchronized (this) {
                    try {
                        this.wait();
                    } catch (InterruptedException e) {
                        interruptedLatch.countDown();
                    }
                }

                return false;
            }
        });

        // Redefine the end points that HostDiscovery knows about to be only FOO
        when(_hostDiscovery.getHosts()).thenReturn(ImmutableList.of(FOO_ENDPOINT));

        ServicePool<Service> pool = new ServicePool<Service>(_ticker, _hostDiscovery, false, _serviceFactory,
                ServiceCachingPolicyBuilder.NO_CACHING, _partitionFilter, _loadBalanceAlgorithm,
                Executors.newScheduledThreadPool(1), true);

        // Make it so that FOO needs to be health checked...
        try {
            pool.execute(NEVER_RETRY, new ServiceCallback<Service, Void>() {
                @Override
                public Void call(Service service) throws ServiceException {
                    throw new ServiceException();
                }
            });
            fail();
        } catch (MaxRetriesException e) {
            // Expected
        }

        // The health check should be running now...
        assertTrue(inHealthCheckLatch.await(10, TimeUnit.SECONDS));

        // And it should get interrupted on close...
        pool.close();

        assertTrue(interruptedLatch.await(10, TimeUnit.SECONDS));
    }

    @Test
    public void testValidEndPointCount() {
        assertEquals(3, _pool.getNumValidEndPoints());
        assertEquals(0, _pool.getNumBadEndPoints());
    }

    @Test
    public void testBadEndPointCount() {
        // Only allow BAZ to have a valid health check -- we know based on the load balance strategy that this
        // will be the last failed end point
        when(_serviceFactory.isHealthy(eq(BAZ_ENDPOINT))).thenReturn(true);

        // Exhaust all of the end points...
        int numEndPointsAvailable = Iterables.size(_hostDiscovery.getHosts());
        for (int i = 0; i < numEndPointsAvailable; i++) {
            try {
                _pool.execute(NEVER_RETRY, new ServiceCallback<Service, Void>() {
                    @Override
                    public Void call(Service service) throws ServiceException {
                        throw new ServiceException();
                    }
                });
                fail();  // should have propagated service exception
            } catch (MaxRetriesException e) {
                // Expected
            }
        }

        // Only BAZ should be healthy
        assertEquals(1, _pool.getNumValidEndPoints());
        assertEquals(2, _pool.getNumBadEndPoints());
    }

    // A dummy interface for testing...
    private static interface Service {
    }
}
