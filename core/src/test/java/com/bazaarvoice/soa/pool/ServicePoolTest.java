package com.bazaarvoice.soa.pool;

import com.bazaarvoice.soa.HostDiscovery;
import com.bazaarvoice.soa.LoadBalanceAlgorithm;
import com.bazaarvoice.soa.RetryPolicy;
import com.bazaarvoice.soa.ServiceCallback;
import com.bazaarvoice.soa.ServiceEndPoint;
import com.bazaarvoice.soa.ServiceFactory;
import com.bazaarvoice.soa.exceptions.MaxRetriesException;
import com.bazaarvoice.soa.exceptions.NoAvailableHostsException;
import com.bazaarvoice.soa.exceptions.NoSuitableHostsException;
import com.bazaarvoice.soa.exceptions.OnlyBadHostsException;
import com.bazaarvoice.soa.exceptions.ServiceException;
import com.google.common.base.Ticker;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.Futures;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Matchers;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.Set;
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

    private HostDiscovery _hostDiscovery;
    private LoadBalanceAlgorithm _loadBalanceAlgorithm;
    private ServiceFactory<Service> _serviceFactory;
    private ScheduledExecutorService _healthCheckExecutor;
    private ServicePool<Service> _pool;

    @SuppressWarnings("unchecked")
    @Before
    public void setup() {
        //
        // This setup method takes the approach of building a reasonably useful ServicePool using mocks that can then be
        // customized by individual test methods to add whatever functionality they need to (or ignored completely).
        //

        _hostDiscovery = mock(HostDiscovery.class);
        when(_hostDiscovery.getHosts()).thenReturn(ImmutableList.of(FOO_ENDPOINT, BAR_ENDPOINT, BAZ_ENDPOINT));

        _loadBalanceAlgorithm = mock(LoadBalanceAlgorithm.class);
        when(_loadBalanceAlgorithm.choose(any(Iterable.class))).thenAnswer(new Answer<ServiceEndPoint>() {
            @Override
            public ServiceEndPoint answer(InvocationOnMock invocation) throws Throwable {
                // Always choose the first endpoint.  This is probably fine since most tests will have just a single
                // endpoint available anyways.
                Iterable<ServiceEndPoint> endpoints = (Iterable<ServiceEndPoint>) invocation.getArguments()[0];
                return endpoints.iterator().next();
            }
        });

        _serviceFactory = (ServiceFactory<Service>) mock(ServiceFactory.class);
        when(_serviceFactory.create(FOO_ENDPOINT)).thenReturn(FOO_SERVICE);
        when(_serviceFactory.create(BAR_ENDPOINT)).thenReturn(BAR_SERVICE);
        when(_serviceFactory.create(BAZ_ENDPOINT)).thenReturn(BAZ_SERVICE);
        when(_serviceFactory.getLoadBalanceAlgorithm()).thenReturn(_loadBalanceAlgorithm);

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

        _pool = (ServicePool) new ServicePoolBuilder<Service>()
                .withHostDiscovery(_hostDiscovery)
                .withServiceFactory(_serviceFactory)
                .withHealthCheckExecutor(_healthCheckExecutor)
                .withTicker(mock(Ticker.class))
                .build();
    }

    @Test
    public void testCallInvokedWithCorrectService() {
        Service expectedService = mock(Service.class);

        // Wire our expected service into the system
        when(_serviceFactory.create(FOO_ENDPOINT)).thenReturn(expectedService);

        // Don't leak service endpoints in real code!!!  This is just a test case.
        Service actualService = _pool.execute(NEVER_RETRY, new ServiceCallback<Service, Service>() {
            @Override
            public Service call(Service s) {
                return s;
            }
        });

        assertSame(expectedService, actualService);
    }

    @Test(expected = NoAvailableHostsException.class)
    public void testThrowsNoAvailableHostsExceptionWhenNoEndpointsAvailable() {
        // Host discovery sees no endpoints...
        when(_hostDiscovery.getHosts()).thenReturn(ImmutableList.<ServiceEndPoint>of());
        _pool.execute(NEVER_RETRY, null);
    }

    @Test(expected = OnlyBadHostsException.class)
    public void testThrowsOnlyBadHostsExceptionWhenOnlyBadEndpointsAvailable() {
        // Exhaust all of the endpoints...
        int numEndpointsAvailable = Iterables.size(_hostDiscovery.getHosts());
        for (int i = 0; i < numEndpointsAvailable; i++) {
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

        // This should trigger a service exception because there are no more available endpoints.
        _pool.execute(NEVER_RETRY, null);
    }

    @Test(expected = NoSuitableHostsException.class)
    public void testThrowsNoSuitableHostsExceptionWhenLoadBalancerReturnsNull() {
        // Reset the load balance algorithm's setup and make it always return null.
        reset(_loadBalanceAlgorithm);
        when(_loadBalanceAlgorithm.choose(Matchers.<Iterable<ServiceEndPoint>>any())).thenReturn(null);

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
    public void testAttemptsToRetryOnServiceException() {
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
    public void testDoesNotAttemptToRetryOnNonServiceException() {
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
    public void testRetriesWithDifferentServiceEndpoints() {
        RetryPolicy retry = mock(RetryPolicy.class);
        when(retry.allowRetry(anyInt(), anyLong())).thenReturn(true, true, false);

        // Each endpoint has a specific service that it's supposed to return.  Remember each service we've seen so
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
    public void testSubmitsHealthCheckOnServiceException() {
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
    public void testDoesNotSubmitHealthCheckOnNonServiceException() {
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
    public void testCallsHealthCheckAfterServiceException() throws InterruptedException {
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
    public void testDoesNotCallHealthCheckAfterNonServiceException() throws InterruptedException {
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
    public void testAllowsEndpointToBeUsedAgainAfterSuccessfulHealthCheck() {
        // Only allow BAZ to have a valid health check -- we know based on the load balance strategy that this
        // will be the last failed endpoint
        when(_serviceFactory.isHealthy(eq(BAZ_ENDPOINT))).thenReturn(true);

        // Exhaust all of the endpoints...
        int numEndpointsAvailable = Iterables.size(_hostDiscovery.getHosts());
        for (int i = 0; i < numEndpointsAvailable; i++) {
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
    public void testBatchHealthCheckAllowsEndpointToBeUsedAgainAfterSuccessfulHealthCheck() {
        // Exhaust all of the endpoints...
        int numEndpointsAvailable = Iterables.size(_hostDiscovery.getHosts());
        for (int i = 0; i < numEndpointsAvailable; i++) {
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
    public void testBadEndpointIsNoLongerHealthCheckedAfterHostDiscoveryRemovesIt() {
        // Redefine the endpoints that HostDiscovery knows about to be only FOO
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

        // Capture the endpoint listener that was registered with HostDiscovery
        ArgumentCaptor<HostDiscovery.EndpointListener> listener = ArgumentCaptor.forClass(
                HostDiscovery.EndpointListener.class);
        verify(_hostDiscovery).addListener(listener.capture());

        // Now, have HostDiscovery fire an event saying that FOO has been removed
        listener.getValue().onEndpointRemoved(FOO_ENDPOINT);

        // Capture the BatchHealthChecks runnable that was registered with the executor so that we can execute it.
        ArgumentCaptor<Runnable> check = ArgumentCaptor.forClass(Runnable.class);
        verify(_healthCheckExecutor).scheduleAtFixedRate(check.capture(), anyLong(), anyLong(), any(TimeUnit.class));

        // Execute the background health checks, this shouldn't check FOO at all
        check.getValue().run();

        verify(_serviceFactory, never()).isHealthy(eq(FOO_ENDPOINT));
    }

    @Test
    public void testBadEndpointDisappearingFromHostDiscoveryDuringCallback() {
        // Redefine the endpoints that HostDiscovery knows about to be only FOO
        when(_hostDiscovery.getHosts()).thenReturn(ImmutableList.of(FOO_ENDPOINT));

        // Capture the endpoint listener that was registered with HostDiscovery
        final ArgumentCaptor<HostDiscovery.EndpointListener> listener = ArgumentCaptor.forClass(
                HostDiscovery.EndpointListener.class);
        verify(_hostDiscovery).addListener(listener.capture());

        try {
            _pool.execute(NEVER_RETRY, new ServiceCallback<Service, Void>() {
                @Override
                public Void call(Service service) throws ServiceException {
                    // Have HostDiscovery tell the ServicePool that FOO is gone.
                    listener.getValue().onEndpointRemoved(FOO_ENDPOINT);

                    // Now fail this request, this shouldn't result with any bad endpoints
                    throw new ServiceException();
                }
            });
            fail();  // should have propagated service exception
        } catch (MaxRetriesException e) {
            // Expected
        }

        // At this point the bad endpoints list should be empty
        assertTrue(_pool.getBadEndpoints().isEmpty());
    }

    // A dummy interface for testing...
    private static interface Service {}
}
