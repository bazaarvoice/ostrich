package com.bazaarvoice.ostrich.pool;

import com.bazaarvoice.ostrich.ServiceEndPoint;
import com.bazaarvoice.ostrich.ServiceFactory;
import com.bazaarvoice.ostrich.exceptions.NoCachedInstancesAvailableException;
import com.codahale.metrics.MetricRegistry;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.apache.commons.pool.impl.GenericKeyedObjectPool;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class SingleThreadedClientServiceCacheTest {
    private static final ServiceEndPoint END_POINT = mock(ServiceEndPoint.class);

    private ServiceFactory<Service> _factory;
    private ServiceCachingPolicy _cachingPolicy;
    private MetricRegistry _registry = new MetricRegistry();
    private List<SingleThreadedClientServiceCache<?>> _caches = Lists.newArrayList();

    @SuppressWarnings("unchecked")
    @Before
    public void setup() {
        _factory = mock(ServiceFactory.class);
        when(_factory.getServiceName()).thenReturn(Service.class.getSimpleName());
        when(_factory.create(any(ServiceEndPoint.class))).thenAnswer(new Answer<Service>() {
            @Override
            public Service answer(InvocationOnMock invocation) throws Throwable {
                return mock(Service.class);
            }
        });

        // By default the caching policy will grow infinitely
        _cachingPolicy = mock(ServiceCachingPolicy.class);
        when(_cachingPolicy.getMaxNumServiceInstances()).thenReturn(-1);
        when(_cachingPolicy.getMaxNumServiceInstancesPerEndPoint()).thenReturn(1);
        when(_cachingPolicy.getCacheExhaustionAction()).thenReturn(ServiceCachingPolicy.ExhaustionAction.FAIL);
    }

    @After
    public void teardown() {
        for (SingleThreadedClientServiceCache<?> cache : _caches) {
            cache.close();
        }
    }

    @Test
    public void testKeyedObjectPoolIsCorrectlyConfigured() {
        // Set values to be different from corresponding GenericKeyedObjectPool defaults.
        when(_cachingPolicy.getCacheExhaustionAction()).thenReturn(ServiceCachingPolicy.ExhaustionAction.GROW);
        when(_cachingPolicy.getMaxNumServiceInstances()).thenReturn(20);
        when(_cachingPolicy.getMaxNumServiceInstancesPerEndPoint()).thenReturn(5);
        when(_cachingPolicy.getMaxServiceInstanceIdleTime(TimeUnit.MILLISECONDS)).thenReturn(10L);

        GenericKeyedObjectPool<ServiceEndPoint, Service> pool = newCache().getPool();
        assertEquals(GenericKeyedObjectPool.WHEN_EXHAUSTED_GROW, pool.getWhenExhaustedAction());
        assertEquals(20, pool.getMaxTotal());
        assertEquals(5, pool.getMaxActive());
        assertEquals(5, pool.getMaxIdle());
        assertEquals(10L, pool.getMinEvictableIdleTimeMillis());
        assertEquals(20, pool.getNumTestsPerEvictionRun());
    }

    @Test(expected = NullPointerException.class)
    public void testCheckOutFromNullEndPoint() throws Exception {
        newCache().checkOut(null);
    }

    @Test(expected = NullPointerException.class)
    public void testCheckInNullHandle() throws Exception {
        newCache().checkIn(null);
    }

    @Test(expected = NullPointerException.class)
    public void testCheckInToNullEndPoint() throws Exception {
        Service service = mock(Service.class);
        ServiceHandle<Service> handle = new ServiceHandle<>(service, null);
        newCache().checkIn(handle);
    }

    @Test(expected = NullPointerException.class)
    public void testCheckInNullServiceInstance() throws Exception {
        ServiceHandle<Service> handle = new ServiceHandle<>(null, END_POINT);
        newCache().checkIn(handle);
    }

    @Test(expected = NullPointerException.class)
    public void testEvictNullEndPoint() {
        newCache().evict(null);
    }

    @Test
    public void testFactoryExceptionIsPropagated() {
        NullPointerException exception = mock(NullPointerException.class);
        when(_factory.create(any(ServiceEndPoint.class))).thenThrow(exception);

        SingleThreadedClientServiceCache<Service> cache = newCache();
        try {
            cache.checkOut(END_POINT);
            fail();
        } catch (Exception caught) {
            assertSame(exception, caught);
        }
    }

    @Test
    public void testServiceInstanceIsReused() throws Exception {
        SingleThreadedClientServiceCache<Service> cache = newCache();
        ServiceHandle<Service> handle = cache.checkOut(END_POINT);
        cache.checkIn(handle);

        assertSame(handle.getService(), cache.checkOut(END_POINT).getService());
    }

    @Test
    public void testInUseServiceInstanceNotReused() throws Exception {
        // Allow 2 instances per end point
        when(_cachingPolicy.getMaxNumServiceInstancesPerEndPoint()).thenReturn(2);

        SingleThreadedClientServiceCache<Service> cache = newCache();
        ServiceHandle<Service> handle = cache.checkOut(END_POINT);
        assertNotSame(handle.getService(), cache.checkOut(END_POINT).getService());
    }

    @Test
    public void testDuplicateServiceInstancesAllowed() throws Exception {
        Service service = mock(Service.class);
        when(_factory.create(any(ServiceEndPoint.class))).thenReturn(service);
        when(_cachingPolicy.getMaxNumServiceInstancesPerEndPoint()).thenReturn(-1);

        SingleThreadedClientServiceCache<Service> cache = newCache();
        ServiceHandle<Service> handle1 = cache.checkOut(END_POINT);
        ServiceHandle<Service> handle2 = cache.checkOut(END_POINT);

        assertSame(service, handle1.getService());
        assertSame(service, handle2.getService());
        assertEquals(2, cache.getNumActiveInstances(END_POINT));

        cache.checkIn(handle1);
        cache.checkIn(handle2);
        assertEquals(2, cache.getNumIdleInstances(END_POINT));
    }

    @Test
    public void testSameServiceInstanceAllowedForMultipleEndPoints() throws Exception {
        Service service = mock(Service.class);
        when(_factory.create(any(ServiceEndPoint.class))).thenReturn(service);

        SingleThreadedClientServiceCache<Service> cache = newCache();

        ServiceEndPoint endPoint1 = mock(ServiceEndPoint.class);
        ServiceEndPoint endPoint2 = mock(ServiceEndPoint.class);

        ServiceHandle<Service> handle1 = cache.checkOut(endPoint1);
        assertSame(service, handle1.getService());
        assertEquals(1, cache.getNumActiveInstances(endPoint1));
        assertEquals(0, cache.getNumActiveInstances(endPoint2));

        ServiceHandle<Service> handle2 = cache.checkOut(endPoint2);
        assertSame(service, handle2.getService());
        assertEquals(1, cache.getNumActiveInstances(endPoint1));
        assertEquals(1, cache.getNumActiveInstances(endPoint2));

        cache.checkIn(handle1);
        assertEquals(1, cache.getNumIdleInstances(endPoint1));
        assertEquals(0, cache.getNumIdleInstances(endPoint2));

        cache.checkIn(handle2);
        assertEquals(1, cache.getNumIdleInstances(endPoint1));
        assertEquals(1, cache.getNumIdleInstances(endPoint2));
    }

    @Test
    public void testEvictedEndPointDestroyedAutomaticEviction() throws Exception {
        // Make the cache only hold one instance total.
        when(_cachingPolicy.getMaxNumServiceInstances()).thenReturn(1);

        SingleThreadedClientServiceCache<Service> cache = newCache();
        ServiceHandle<Service> handle = cache.checkOut(END_POINT);

        cache.checkIn(handle);
        // Check out a different end point to force the currently cached instance out.
        cache.checkOut(mock(ServiceEndPoint.class));

        verify(_factory).destroy(END_POINT, handle.getService());
    }

    @Test
    public void testEvictedEndPointDestroyedManualEviction() throws Exception {
        SingleThreadedClientServiceCache<Service> cache = newCache();
        ServiceHandle<Service> handle = cache.checkOut(END_POINT);
        cache.checkIn(handle);
        cache.evict(END_POINT);

        verify(_factory).destroy(END_POINT, handle.getService());
    }

    @Test
    public void testEvictedEndPointHasServiceInstancesRemovedFromCache() throws Exception {
        SingleThreadedClientServiceCache<Service> cache = newCache();

        ServiceHandle<Service> handle = cache.checkOut(END_POINT);
        cache.checkIn(handle);
        cache.evict(END_POINT);

        assertNotSame(handle.getService(), cache.checkOut(END_POINT).getService());
    }

    @Test
    public void testEvictedEndPointWhileServiceInstanceCheckedOut() throws Exception {
        SingleThreadedClientServiceCache<Service> cache = newCache();

        ServiceHandle<Service> handle = cache.checkOut(END_POINT);
        cache.evict(END_POINT);
        cache.checkIn(handle);

        assertNotSame(handle.getService(), cache.checkOut(END_POINT).getService());
    }

    @Test
    public void testEvictedEndPointWhileDuplicateServiceInstancesCheckedOut() throws Exception {
        Service service = mock(Service.class);
        when(_factory.create(any(ServiceEndPoint.class))).thenReturn(service);
        when(_cachingPolicy.getMaxNumServiceInstancesPerEndPoint()).thenReturn(-1);

        SingleThreadedClientServiceCache<Service> cache = newCache();

        ServiceHandle<Service> handle1 = cache.checkOut(END_POINT);
        ServiceHandle<Service> handle2 = cache.checkOut(END_POINT);

        cache.evict(END_POINT);

        cache.checkIn(handle1);
        cache.checkIn(handle2);

        verify(_factory, times(2)).destroy(END_POINT, service);
    }

    @Test
    public void testEvictedEndPointWhileServiceInstanceCheckedOutMoreDuplicatesCheckedOut() throws Exception {
        Service service = mock(Service.class);
        when(_factory.create(any(ServiceEndPoint.class))).thenReturn(service);
        when(_cachingPolicy.getMaxNumServiceInstancesPerEndPoint()).thenReturn(-1);

        SingleThreadedClientServiceCache<Service> cache = newCache();
        ServiceHandle<Service> handle1 = cache.checkOut(END_POINT);

        cache.evict(END_POINT);

        // Check out a new one after eviction, while a copy is still checked out.
        ServiceHandle<Service> handle2 = cache.checkOut(END_POINT);

        cache.checkIn(handle1);
        verify(_factory, times(1)).destroy(END_POINT, service);

        cache.checkIn(handle2);
        verify(_factory, times(1)).destroy(END_POINT, service);
    }

    @Test
    public void testEvictedEndPointWhileServiceInstanceCheckedOutAllowsSubsequentCopies() throws Exception {
        Service service = mock(Service.class);
        when(_factory.create(any(ServiceEndPoint.class))).thenReturn(service);

        SingleThreadedClientServiceCache<Service> cache = newCache();

        ServiceHandle<Service> handle = cache.checkOut(END_POINT);
        cache.evict(END_POINT);
        cache.checkIn(handle);

        handle = cache.checkOut(END_POINT);
        cache.checkIn(handle);

        verify(_factory, times(1)).destroy(END_POINT, service);
    }

    @Test
    public void testEvictedEndPointWhileServiceInstanceCheckedOutAllowsSameInstanceOtherEndPoints() throws Exception {
        Service service = mock(Service.class);
        when(_factory.create(any(ServiceEndPoint.class))).thenReturn(service);

        SingleThreadedClientServiceCache<Service> cache = newCache();

        ServiceEndPoint invalidEndPoint = mock(ServiceEndPoint.class);
        ServiceEndPoint validEndPoint = mock(ServiceEndPoint.class);

        ServiceHandle<Service> handle1 = cache.checkOut(invalidEndPoint);
        ServiceHandle<Service> handle2 = cache.checkOut(validEndPoint);

        cache.evict(invalidEndPoint);

        cache.checkIn(handle1);
        cache.checkIn(handle2);

        verify(_factory, never()).destroy(validEndPoint, service);
        verify(_factory, times(1)).destroy(invalidEndPoint, service);
    }

    @Test(expected = NoCachedInstancesAvailableException.class)
    public void testFailCacheExhaustionAction() throws Exception {
        when(_cachingPolicy.getCacheExhaustionAction()).thenReturn(ServiceCachingPolicy.ExhaustionAction.FAIL);

        SingleThreadedClientServiceCache<Service> cache = newCache();
        cache.checkOut(END_POINT);
        cache.checkOut(END_POINT);
    }

    @Test
    public void testGrowCacheExhaustionAction() throws Exception {
        when(_cachingPolicy.getCacheExhaustionAction()).thenReturn(ServiceCachingPolicy.ExhaustionAction.GROW);

        SingleThreadedClientServiceCache<Service> cache = newCache();
        cache.checkOut(END_POINT);
        cache.checkOut(END_POINT);
    }

    @Test
    public void testInstancesCreatedWhileGrowingAreNotReused() throws Exception {
        when(_cachingPolicy.getMaxNumServiceInstancesPerEndPoint()).thenReturn(1);
        when(_cachingPolicy.getCacheExhaustionAction()).thenReturn(ServiceCachingPolicy.ExhaustionAction.GROW);

        SingleThreadedClientServiceCache<Service> cache = newCache();

        // Grow the cache a bunch, remembering each service that was created...
        Set<ServiceHandle<Service>> seenHandles = Sets.newHashSet();
        Set<Service> seenServices = Sets.newHashSet();
        for (int i = 0; i < 10; i++) {
            ServiceHandle<Service> handle = cache.checkOut(END_POINT);
            seenHandles.add(handle);
            seenServices.add(handle.getService());
        }

        // Now return each of the services.  Since the cache has a size of 1, only one of them should be retained...
        for (ServiceHandle<Service> handle : seenHandles) {
            cache.checkIn(handle);
        }

        // Figure out which one is retained...
        ServiceHandle<Service> retainedHandle = cache.checkOut(END_POINT);
        assertTrue(seenServices.contains(retainedHandle.getService()));

        // Force the cache to grow again, this new service should have never been seen before...
        ServiceHandle<Service> newHandle = cache.checkOut(END_POINT);
        assertFalse(seenServices.contains(newHandle.getService()));
    }

    @Test
    public void testWaitCacheExhaustionAction() throws Exception {
        when(_cachingPolicy.getMaxNumServiceInstancesPerEndPoint()).thenReturn(1);
        when(_cachingPolicy.getCacheExhaustionAction()).thenReturn(ServiceCachingPolicy.ExhaustionAction.WAIT);

        final SingleThreadedClientServiceCache<Service> cache = newCache();
        ServiceHandle<Service> handle = cache.checkOut(END_POINT);

        // Run a 2nd check out operation in a background thread.  It should block because there is only one service
        // instance available, and the above check out operation is holding onto it.  Eventually we're going to call
        // check in to return the instance, at which point the background thread should be able to terminate.
        final CountDownLatch inCallable = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<ServiceHandle<Service>> serviceFuture = executor.submit(new Callable<ServiceHandle<Service>>() {
                @Override
                public ServiceHandle<Service> call() throws Exception {
                    inCallable.countDown();
                    return cache.checkOut(END_POINT);
                }
            });

            // Block until we know for sure the callable has had a chance to start executing and it is highly likely
            // that is is blocked in the checkOut call.
            assertTrue(inCallable.await(10, TimeUnit.SECONDS));

            try {
                // This should fail because the service instance hasn't yet been returned.  There's a small chance that
                // this could fail while there is a bug in the code if it takes the bug more time to manifest itself
                // than the allotted time to wait.
                serviceFuture.get(100, TimeUnit.MILLISECONDS);
                fail();
            } catch (TimeoutException e) {
                // Expected to fail because the instance hasn't been checked in yet.
            }

            cache.checkIn(handle);
            assertSame(handle.getService(), serviceFuture.get(10, TimeUnit.SECONDS).getService());
        } finally {
            executor.shutdown();
        }
    }

    @Test
    public void testSchedulesPeriodicEvictionCheckUponCreation() {
        when(_cachingPolicy.getMaxServiceInstanceIdleTime(any(TimeUnit.class))).thenReturn(10L);
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
                eq(SingleThreadedClientServiceCache.EVICTION_DURATION_IN_SECONDS),
                eq(SingleThreadedClientServiceCache.EVICTION_DURATION_IN_SECONDS),
                eq(TimeUnit.SECONDS));
    }

    @Test(expected = NullPointerException.class)
    public void testNumIdleNullEndPoint() {
        SingleThreadedClientServiceCache<Service> cache = newCache();
        cache.getNumIdleInstances(null);
    }

    @Test(expected = NullPointerException.class)
    public void testNumActiveNullEndPoint() {
        SingleThreadedClientServiceCache<Service> cache = newCache();
        cache.getNumActiveInstances(null);
    }

    @Test
    public void testNumIdleStartsAtZero() {
        SingleThreadedClientServiceCache<Service> cache = newCache();

        assertEquals(0, cache.getNumIdleInstances(END_POINT));
    }

    @Test
    public void testNumActiveStartsAtZero() {
        SingleThreadedClientServiceCache<Service> cache = newCache();

        assertEquals(0, cache.getNumActiveInstances(END_POINT));
    }

    @Test
    public void testNumActiveUpdatedOnCheckOut() throws Exception {
        SingleThreadedClientServiceCache<Service> cache = newCache();
        cache.checkOut(END_POINT);

        assertEquals(1, cache.getNumActiveInstances(END_POINT));
    }

    @Test
    public void testNumIdleUpdatedOnCheckIn() throws Exception {
        SingleThreadedClientServiceCache<Service> cache = newCache();
        cache.checkIn(cache.checkOut(END_POINT));

        assertEquals(1, cache.getNumIdleInstances(END_POINT));
    }

    @Test
    public void testActiveServiceNotCountedIdle() throws Exception {
        SingleThreadedClientServiceCache<Service> cache = newCache();
        cache.checkOut(END_POINT);

        assertEquals(0, cache.getNumIdleInstances(END_POINT));
    }

    @Test
    public void testIdleServiceNotCountedActive() throws Exception {
        SingleThreadedClientServiceCache<Service> cache = newCache();
        cache.checkIn(cache.checkOut(END_POINT));

        assertEquals(0, cache.getNumActiveInstances(END_POINT));
    }

    @Test
    public void testActiveCountAccurateWhenGrowing() throws Exception {
        when(_cachingPolicy.getMaxNumServiceInstancesPerEndPoint()).thenReturn(1);
        when(_cachingPolicy.getCacheExhaustionAction()).thenReturn(ServiceCachingPolicy.ExhaustionAction.GROW);

        SingleThreadedClientServiceCache<Service> cache = newCache();
        cache.checkOut(END_POINT);
        cache.checkOut(END_POINT);

        assertEquals(2, cache.getNumActiveInstances(END_POINT));
    }

    @Test
    public void testCloseDestroysCachedInstances() throws Exception {
        SingleThreadedClientServiceCache<Service> cache = newCache();
        ServiceHandle<Service> handle = cache.checkOut(END_POINT);
        cache.checkIn(handle);
        cache.close();

        verify(_factory).destroy(END_POINT, handle.getService());
    }

    @SuppressWarnings ({"unchecked", "rawtypes"})
    @Test
    public void testCloseCancelsEvictionFuture() {
        when(_cachingPolicy.getMaxServiceInstanceIdleTime(any(TimeUnit.class))).thenReturn(10L);
        ScheduledExecutorService executor = mock(ScheduledExecutorService.class);
        ScheduledFuture future = mock(ScheduledFuture.class);
        when(executor.scheduleAtFixedRate(
                any(Runnable.class),
                anyLong(),
                anyLong(),
                any(TimeUnit.class))).thenReturn(future);

        SingleThreadedClientServiceCache<Service> cache = newCache(executor);
        cache.close();

        verify(future).cancel(anyBoolean());
    }

    @Test
    public void testMultipleClose() {
        SingleThreadedClientServiceCache<Service> cache = newCache();
        cache.close();
        cache.close();
    }

    private SingleThreadedClientServiceCache<Service> newCache() {
        SingleThreadedClientServiceCache<Service> cache = new SingleThreadedClientServiceCache<>(_cachingPolicy, _factory, _registry);
        _caches.add(cache);
        return cache;
    }

    private SingleThreadedClientServiceCache<Service> newCache(ScheduledExecutorService executor) {
        SingleThreadedClientServiceCache<Service> cache = new SingleThreadedClientServiceCache<>(_cachingPolicy, _factory, executor, _registry);
        _caches.add(cache);
        return cache;
    }

    public static interface Service {}
}
