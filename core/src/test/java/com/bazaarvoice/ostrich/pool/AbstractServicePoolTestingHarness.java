package com.bazaarvoice.ostrich.pool;

import com.bazaarvoice.ostrich.HealthCheckResults;
import com.bazaarvoice.ostrich.HostDiscovery;
import com.bazaarvoice.ostrich.LoadBalanceAlgorithm;
import com.bazaarvoice.ostrich.PartitionContext;
import com.bazaarvoice.ostrich.RetryPolicy;
import com.bazaarvoice.ostrich.ServiceCallback;
import com.bazaarvoice.ostrich.ServiceEndPoint;
import com.bazaarvoice.ostrich.ServiceFactory;
import com.bazaarvoice.ostrich.ServicePoolStatistics;
import com.bazaarvoice.ostrich.exceptions.MaxRetriesException;
import com.bazaarvoice.ostrich.exceptions.NoAvailableHostsException;
import com.bazaarvoice.ostrich.exceptions.NoSuitableHostsException;
import com.bazaarvoice.ostrich.exceptions.OnlyBadHostsException;
import com.bazaarvoice.ostrich.exceptions.ServiceException;
import com.bazaarvoice.ostrich.healthcheck.FixedHealthCheckRetryDelay;
import com.bazaarvoice.ostrich.partition.PartitionFilter;
import com.codahale.metrics.MetricRegistry;
import com.google.common.base.Ticker;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

public abstract class AbstractServicePoolTestingHarness {

    protected static final ServiceEndPoint FOO_ENDPOINT = mock(ServiceEndPoint.class);
    protected static final ServiceEndPoint BAR_ENDPOINT = mock(ServiceEndPoint.class);
    protected static final ServiceEndPoint BAZ_ENDPOINT = mock(ServiceEndPoint.class);
    protected static final Service FOO_SERVICE = mock(Service.class);
    protected static final Service BAR_SERVICE = mock(Service.class);
    protected static final Service BAZ_SERVICE = mock(Service.class);
    protected static final RetryPolicy NEVER_RETRY = mock(RetryPolicy.class);

    protected final ServiceCachingPolicy UNLIMITED_CACHING = getServiceCachingPolicy();

    protected Ticker _ticker;
    protected HostDiscovery _hostDiscovery;
    protected PartitionFilter _partitionFilter;
    protected LoadBalanceAlgorithm _loadBalanceAlgorithm;
    protected ServiceFactory<Service> _serviceFactory;
    protected ScheduledExecutorService _healthCheckExecutor;
    protected MetricRegistry _registry;
    protected ServicePool<Service> _pool;

    protected abstract ServiceCachingPolicy getServiceCachingPolicy();
    protected abstract ServiceFactory<Service> getServiceFactoryMock();


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

        _serviceFactory = getServiceFactoryMock();
        when(_serviceFactory.getServiceName()).thenReturn(Service.class.getSimpleName());
        when(_serviceFactory.create(FOO_ENDPOINT)).thenReturn(FOO_SERVICE);
        when(_serviceFactory.create(BAR_ENDPOINT)).thenReturn(BAR_SERVICE);
        when(_serviceFactory.create(BAZ_ENDPOINT)).thenReturn(BAZ_SERVICE);
        when(_serviceFactory.isRetriableException(any(Exception.class))).thenReturn(true);

        _healthCheckExecutor = mock(ScheduledExecutorService.class);

        _registry = new MetricRegistry();

        _pool = new ServicePool<>(_ticker, _hostDiscovery, false, _serviceFactory, UNLIMITED_CACHING, _partitionFilter,
                _loadBalanceAlgorithm, _healthCheckExecutor, true, FixedHealthCheckRetryDelay.ZERO, _registry);
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


    @SuppressWarnings ({"unchecked", "rawType"})
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
    public void testMaxRetriesExceptionIncludesUnderlyingCause() {
        final RuntimeException e = new RuntimeException();
        try {
            _pool.execute(NEVER_RETRY, new ServiceCallback<Service, Void>() {
                @Override
                public Void call(Service service) throws ServiceException {
                    throw e;
                }
            });

            fail();
        } catch (MaxRetriesException expected) {
            assertSame(e, expected.getCause());
        }
    }

    @Test
    public void testOnlyBadHostsExceptionIncludesUnderlyingCauseIfItMadeARequest() {
        // Exhaust all but one of the available end points...
        int numEndPointsAvailable = Iterables.size(_hostDiscovery.getHosts());
        for (int i = 0; i < numEndPointsAvailable - 1; i++) {
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

        // Executing a request only has one choice of endpoint now...
        RetryPolicy retry = mock(RetryPolicy.class);
        when(retry.allowRetry(anyInt(), anyLong())).thenReturn(true, true, false);

        final RuntimeException e = new RuntimeException();
        try {
            _pool.execute(retry, new ServiceCallback<Service, Void>() {
                @Override
                public Void call(Service service) throws ServiceException {
                    throw e;
                }
            });
        } catch (OnlyBadHostsException expected) {
            assertSame(e, expected.getCause());
        }
    }

    @Test
    public void testHealthCheckIsRescheduled() {
        ArgumentCaptor<ServicePool.HealthCheckVerifier> healthCheckVerifierCaptor = ArgumentCaptor.forClass(ServicePool.HealthCheckVerifier.class);
        verify(_healthCheckExecutor).scheduleAtFixedRate(healthCheckVerifierCaptor.capture(), anyLong(), anyLong(), any(TimeUnit.class));
        ServicePool.HealthCheckVerifier healthCheckVerifier = healthCheckVerifierCaptor.getValue();

        when(_healthCheckExecutor.submit(any(ServicePool.HealthCheck.class))).thenThrow(NullPointerException.class);

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

        verify(_healthCheckExecutor, times(1)).submit(any(ServicePool.HealthCheck.class));

        // Health check was not scheduled, so run the verify process to reschedule.
        // Scheduling fails, so next verify run will schedule again.
        healthCheckVerifier.run();
        verify(_healthCheckExecutor, times(2)).submit(any(ServicePool.HealthCheck.class));

        // Ensure next scheduling attempt is successful
        reset(_healthCheckExecutor);
        when(_healthCheckExecutor.submit(any(ServicePool.HealthCheck.class))).thenReturn(mock(Future.class));

        // Verification successfully schedules the health check
        healthCheckVerifier.run();
        verify(_healthCheckExecutor, times(1)).submit(any(ServicePool.HealthCheck.class));

        // Running verification again does not schedule the health check again
        healthCheckVerifier.run();
        verify(_healthCheckExecutor, times(1)).submit(any(ServicePool.HealthCheck.class));
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
        verify(_healthCheckExecutor).submit(any(com.bazaarvoice.ostrich.pool.ServicePool.HealthCheck.class));
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
        verify(_healthCheckExecutor, never()).submit(any(com.bazaarvoice.ostrich.pool.ServicePool.HealthCheck.class));
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

        _pool.forceHealthChecks();
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
        for (ServiceEndPoint ignored : _hostDiscovery.getHosts()) {
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

        _pool.forceHealthChecks();

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

        _pool.forceHealthChecks();

        assertSame(FOO_SERVICE, _pool.execute(NEVER_RETRY, new ServiceCallback<Service, Service>() {
            @Override
            public Service call(Service service) throws ServiceException {
                return service;
            }
        }));
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

        _pool.forceHealthChecks();

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
        ServicePool<Service> pool = new ServicePool<>(_ticker, _hostDiscovery, false, _serviceFactory,
                ServiceCachingPolicyBuilder.NO_CACHING, _partitionFilter, _loadBalanceAlgorithm, _healthCheckExecutor,
                false, FixedHealthCheckRetryDelay.ZERO, _registry);
        pool.close();

        verify(_healthCheckExecutor, never()).shutdown();
        verify(_healthCheckExecutor, never()).shutdownNow();
    }

    @Test
    public void testDoesShutdownHealthCheckExecutorOnClose() {
        ServicePool<Service> pool = new ServicePool<>(_ticker, _hostDiscovery, false, _serviceFactory,
                ServiceCachingPolicyBuilder.NO_CACHING, _partitionFilter, _loadBalanceAlgorithm, _healthCheckExecutor,
                true, FixedHealthCheckRetryDelay.ZERO, _registry);
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

        ServicePool<Service> pool = new ServicePool<>(_ticker, _hostDiscovery, false, _serviceFactory,
                ServiceCachingPolicyBuilder.NO_CACHING, _partitionFilter, _loadBalanceAlgorithm,
                Executors.newScheduledThreadPool(1), true, new FixedHealthCheckRetryDelay(100, TimeUnit.MILLISECONDS), _registry);

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

        _pool.forceHealthChecks();

        // Only BAZ should be healthy
        assertEquals(1, _pool.getNumValidEndPoints());
        assertEquals(2, _pool.getNumBadEndPoints());
    }

    // A dummy interface for testing...
    protected static interface Service {
    }
}
