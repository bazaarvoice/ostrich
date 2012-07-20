package com.bazaarvoice.soa.pool;

import com.bazaarvoice.soa.ServiceEndPoint;
import com.bazaarvoice.soa.ServiceFactory;
import com.bazaarvoice.soa.exceptions.NoCachedConnectionAvailableException;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ServiceCacheTest {
    private static final ServiceEndPoint END_POINT = mock(ServiceEndPoint.class);

    private ServiceFactory<Service> _factory;
    private ServiceCachingPolicy _cachingPolicy;
    private List<ServiceCache<?>> _caches = Lists.newArrayList();

    @SuppressWarnings("unchecked")
    @Before
    public void setup() {
        _factory = mock(ServiceFactory.class);
        when(_factory.create(any(ServiceEndPoint.class))).thenAnswer(new Answer<Service>() {
            @Override
            public Service answer(InvocationOnMock invocation) throws Throwable {
                return mock(Service.class);
            }
        });

        // By default the caching policy will only cache one service instance per endpoint and never expires instances
        _cachingPolicy = mock(ServiceCachingPolicy.class);
        when(_cachingPolicy.getMaxNumServiceInstances()).thenReturn(-1);
        when(_cachingPolicy.getMaxNumServiceInstancesPerEndPoint()).thenReturn(-1);
        when(_cachingPolicy.getCacheExhaustionAction()).thenReturn(ServiceCachingPolicy.ExhaustionAction.FAIL);
    }

    @After
    public void teardown() {
        for (ServiceCache<?> cache : _caches) {
            cache.close();
        }
    }

    @Test(expected = NullPointerException.class)
    public void testCheckOutFromNullEndPoint() {
        newCache().checkOut(null);
    }

    @Test(expected = NullPointerException.class)
    public void testCheckInToNullEndPoint() {
        Service service = mock(Service.class);
        newCache().checkIn(null, service);
    }

    @Test(expected = NullPointerException.class)
    public void testCheckInNullServiceInstance() {
        newCache().checkIn(END_POINT, null);
    }

    @Test(expected = NullPointerException.class)
    public void testEvictNullEndPoint() {
        newCache().evict(null);
    }

    @Test
    public void testServiceInstanceIsReused() {
        ServiceCache<Service> cache = newCache();
        Service service = cache.checkOut(END_POINT);
        cache.checkIn(END_POINT, service);

        assertSame(service, cache.checkOut(END_POINT));
    }

    @Test
    public void testInUseServiceInstanceNotReused() {
        // Allow 2 instances per endpoint
        when(_cachingPolicy.getMaxNumServiceInstancesPerEndPoint()).thenReturn(2);

        ServiceCache<Service> cache = newCache();
        Service service = cache.checkOut(END_POINT);
        assertNotSame(service, cache.checkOut(END_POINT));
    }

    @Test
    public void testEvictedEndPointHasServiceInstancesRemovedFromCache() {
        ServiceCache<Service> cache = newCache();

        Service service = cache.checkOut(END_POINT);
        cache.checkIn(END_POINT, service);
        cache.evict(END_POINT);

        assertNotSame(service, cache.checkOut(END_POINT));
    }

    @Test(expected = NoCachedConnectionAvailableException.class)
    public void testFailCacheExhaustionAction() {
        when(_cachingPolicy.getMaxNumServiceInstancesPerEndPoint()).thenReturn(1);
        when(_cachingPolicy.getCacheExhaustionAction()).thenReturn(ServiceCachingPolicy.ExhaustionAction.FAIL);

        ServiceCache<Service> cache = newCache();
        cache.checkOut(END_POINT);
        cache.checkOut(END_POINT);
    }

    @Test
    public void testGrowCacheExhaustionAction() {
        when(_cachingPolicy.getMaxNumServiceInstancesPerEndPoint()).thenReturn(1);
        when(_cachingPolicy.getCacheExhaustionAction()).thenReturn(ServiceCachingPolicy.ExhaustionAction.GROW);

        ServiceCache<Service> cache = newCache();
        cache.checkOut(END_POINT);
        cache.checkOut(END_POINT);
    }

    @Test
    public void testInstancesCreatedWhileGrowingAreNotReused() {
        when(_cachingPolicy.getMaxNumServiceInstancesPerEndPoint()).thenReturn(1);
        when(_cachingPolicy.getCacheExhaustionAction()).thenReturn(ServiceCachingPolicy.ExhaustionAction.GROW);

        ServiceCache<Service> cache = newCache();

        // Grow the cache a bunch, remembering each service that was created...
        Set<Service> services = Sets.newHashSet();
        for (int i = 0; i < 10; i++) {
            services.add(cache.checkOut(END_POINT));
        }

        // Now return each of the services.  Since the cache has a size of 1, only one of them should be retained...
        for (Service service : services) {
            cache.checkIn(END_POINT, service);
        }

        // Figure out which one is retained...
        Service retainedService = cache.checkOut(END_POINT);
        assertTrue(services.contains(retainedService));
        cache.checkIn(END_POINT, retainedService);

        // All subsequent checkouts should be for the same service...
        for (int i = 0; i < 10; i++) {
            Service service = cache.checkOut(END_POINT);
            assertSame(retainedService, service);
            cache.checkIn(END_POINT, service);
        }
    }

    @Test
    public void testWaitCacheExhaustionAction() throws ExecutionException, InterruptedException, TimeoutException {
        when(_cachingPolicy.getMaxNumServiceInstancesPerEndPoint()).thenReturn(1);
        when(_cachingPolicy.getCacheExhaustionAction()).thenReturn(ServiceCachingPolicy.ExhaustionAction.WAIT);

        final ServiceCache<Service> cache = newCache();
        Service service = cache.checkOut(END_POINT);

        // Run a 2nd check out operation in a background thread.  It should block because there is only one service
        // instance available, and the above check out operation is holding onto it.  Eventually we're going to call
        // check in to return the instance, at which point the background thread should be able to terminate.
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<Service> serviceFuture = executor.submit(new Callable<Service>() {
                @Override
                public Service call() throws Exception {
                    return cache.checkOut(END_POINT);
                }
            });

            try {
                serviceFuture.get(100, TimeUnit.MILLISECONDS);
                fail();
            } catch (TimeoutException e) {
                // Expected to fail because the instance hasn't been checked in yet.
            }

            cache.checkIn(END_POINT, service);
            assertSame(service, serviceFuture.get(100, TimeUnit.MILLISECONDS));
        } finally {
            executor.shutdown();
        }
    }

    @Test
    public void testSchedulesPeriodicEvictionCheckUponCreation() {
        ScheduledExecutorService executor = mock(ScheduledExecutorService.class);
        when(executor.scheduleAtFixedRate(any(Runnable.class), anyLong(), anyLong(), any(TimeUnit.class))).thenAnswer(
                new Answer<ScheduledFuture<?>>() {
                    @Override
                    public ScheduledFuture<?> answer(InvocationOnMock invocation) throws Throwable {
                        return mock(ScheduledFuture.class);
                    }
                }
        );

        newCache(executor);
        verify(executor).scheduleAtFixedRate(
                any(Runnable.class),
                eq(ServiceCache.EVICTION_DURATION_IN_SECONDS),
                eq(ServiceCache.EVICTION_DURATION_IN_SECONDS),
                eq(TimeUnit.SECONDS));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testCloseCancelsEvictionFuture() {
        ScheduledExecutorService executor = mock(ScheduledExecutorService.class);
        ScheduledFuture future = mock(ScheduledFuture.class);
        when(executor.scheduleAtFixedRate(
                any(Runnable.class),
                anyLong(),
                anyLong(),
                any(TimeUnit.class))).thenReturn(future);

        ServiceCache<Service> cache = newCache(executor);
        cache.close();

        verify(future).cancel(anyBoolean());
    }

    @Test
    public void testMultipleClose() {
        ServiceCache<Service> cache = newCache();
        cache.close();
        cache.close();
    }

    private ServiceCache<Service> newCache() {
        ServiceCache<Service> cache = new ServiceCache<Service>(_cachingPolicy, _factory);
        _caches.add(cache);
        return cache;
    }

    private ServiceCache<Service> newCache(ScheduledExecutorService executor) {
        ServiceCache<Service> cache = new ServiceCache<Service>(_cachingPolicy, _factory, executor);
        _caches.add(cache);
        return cache;
    }

    public static interface Service {}
}