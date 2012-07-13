package com.bazaarvoice.soa.pool;

import com.bazaarvoice.soa.HostDiscovery;
import com.bazaarvoice.soa.LoadBalanceAlgorithm;
import com.bazaarvoice.soa.RetryPolicy;
import com.bazaarvoice.soa.ServiceCallback;
import com.bazaarvoice.soa.ServiceEndPoint;
import com.bazaarvoice.soa.ServiceFactory;
import com.bazaarvoice.soa.exceptions.MaxRetriesException;
import com.bazaarvoice.soa.exceptions.ServiceException;
import com.google.common.base.Ticker;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ServicePoolCachingTest {
    private static final ServiceEndPoint FOO_ENDPOINT = mock(ServiceEndPoint.class);
    private static final ServiceEndPoint BAR_ENDPOINT = mock(ServiceEndPoint.class);
    private static final Service FOO_SERVICE = mock(Service.class);
    private static final Service BAR_SERVICE = mock(Service.class);
    private static final RetryPolicy NEVER_RETRY = mock(RetryPolicy.class);

    private Ticker _ticker;
    private HostDiscovery _hostDiscovery;
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
        when(_hostDiscovery.getHosts()).thenReturn(ImmutableList.of(FOO_ENDPOINT, BAR_ENDPOINT));
        when(_hostDiscovery.contains(FOO_ENDPOINT)).thenReturn(true);

        _loadBalanceAlgorithm = mock(LoadBalanceAlgorithm.class);
        // Return FOO_ENDPOINT even if it's not in the list (like if it's been marked bad)
        when(_loadBalanceAlgorithm.choose(any(Iterable.class))).thenReturn(FOO_ENDPOINT);

        _serviceFactory = (ServiceFactory<Service>) mock(ServiceFactory.class);
        when(_serviceFactory.create(FOO_ENDPOINT)).thenReturn(FOO_SERVICE).thenReturn(BAR_SERVICE);
        when(_serviceFactory.getLoadBalanceAlgorithm()).thenReturn(_loadBalanceAlgorithm);

        _healthCheckExecutor = mock(ScheduledExecutorService.class);
        when(_healthCheckExecutor.submit(any(Runnable.class))).then(new Answer<Future<?>>() {
            @Override
            public Future<?> answer(InvocationOnMock invocation) throws Throwable {
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

        _pool = new ServicePool<Service>(Service.class, _ticker, _hostDiscovery, _serviceFactory, _healthCheckExecutor,
                true, new ServiceCachingPolicy(100, 1, 10, 5, TimeUnit.SECONDS));
    }

    @After
    public void teardown() {
        _pool.close();
    }

    @Test
    public void testCacheIsUsedIfRequested() {
        final ServiceCallback<Service, Service> callback = new ServiceCallback<Service, Service>() {
            @Override
            public Service call(Service service)
                    throws ServiceException {
                return service;
            }
        };
        final Service first = _pool.execute(NEVER_RETRY, callback);
        final Service second = _pool.execute(NEVER_RETRY, callback);

        assertSame(first, second);
    }

    @Test
    public void testEndPointRemovedFromCache() {
        // Capture the endpoint listener that was registered with HostDiscovery
        final ArgumentCaptor<HostDiscovery.EndpointListener> listener = ArgumentCaptor.forClass(
                HostDiscovery.EndpointListener.class);
        verify(_hostDiscovery).addListener(listener.capture());

        final ServiceCallback<Service, Service> callback = new ServiceCallback<Service, Service>() {
            @Override
            public Service call(Service service)
                    throws ServiceException {
                return service;
            }
        };
        final Service first = _pool.execute(NEVER_RETRY, callback);

        // Remove the end point and then add it back
        listener.getValue().onEndpointRemoved(FOO_ENDPOINT);
        listener.getValue().onEndpointAdded(FOO_ENDPOINT);

        final Service second = _pool.execute(NEVER_RETRY, callback);

        assertNotSame(first, second);
    }

    @SuppressWarnings("unchecked")
    @Test(expected = MaxRetriesException.class)
    public void testBadEndPointInvalidatedFromCache() {
        try {
            _pool.execute(NEVER_RETRY, new ServiceCallback<Service, Void>() {
                @Override
                public Void call(Service service)
                        throws ServiceException {
                    throw new ServiceException();
                }
            });
        } catch (MaxRetriesException e) {
            // Expected
        }

        _pool.execute(NEVER_RETRY, new ServiceCallback<Service, Void>() {
            @Override
            public Void call(Service service)
                    throws ServiceException {
                return null;
            }
        });
    }

    @Test
    public void testBurstsCacheWhenNecessary()
            throws InterruptedException {
        final CountDownLatch firstLatch = new CountDownLatch(1);
        final CountDownLatch secondLatch = new CountDownLatch(1);
        final AtomicReference<Service> first = new AtomicReference<Service>();

        // Spawn a new thread that holds onto a cached service instance
        Thread executeThread = new Thread(new Runnable() {
            @Override
            public void run() {
                _pool.execute(NEVER_RETRY, new ServiceCallback<Service, Void>() {
                    @Override
                    public Void call(Service service)
                            throws ServiceException {
                        first.set(service);
                        try {
                            firstLatch.countDown();
                            secondLatch.await(10, TimeUnit.SECONDS);
                        } catch (InterruptedException e) {
                            throw new ServiceException();
                        }
                        return null;
                    }
                });
            }
        });
        executeThread.start();

        firstLatch.await(10, TimeUnit.SECONDS);
        final Service second = _pool.execute(NEVER_RETRY, new ServiceCallback<Service, Service>() {
            @Override
            public Service call(Service service)
                    throws ServiceException {
                return service;
            }
        });

        secondLatch.countDown();

        assertNotSame(first.get(), second);
    }

    private interface Service {
    }
}