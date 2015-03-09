package com.bazaarvoice.ostrich.pool;

import com.bazaarvoice.ostrich.ServiceEndPoint;
import com.bazaarvoice.ostrich.ThreadSafeServiceFactory;
import com.codahale.metrics.MetricRegistry;
import com.google.common.collect.Lists;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.List;
import java.util.concurrent.Executors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class MultiThreadedClientServiceCacheTest {
    private ServiceEndPoint _endPoint;
    private ThreadSafeServiceFactory<Service> _factory;
    private List<MultiThreadedClientServiceCache<?>> _caches = Lists.newArrayList();
    private MetricRegistry _metricRegistry = new MetricRegistry();

    @SuppressWarnings ("unchecked")
    @Before
    public void setup() {
        _factory = mock(ThreadSafeServiceFactory.class);
        when(_factory.getServiceName()).thenReturn(Service.class.getSimpleName());
        when(_factory.create(any(ServiceEndPoint.class))).thenAnswer(new Answer<Service>() {
            @Override
            public Service answer(InvocationOnMock invocation)
                    throws Throwable {
                return mock(Service.class);
            }
        });
        _endPoint = newEndPoint("id", "name");
    }

    @After
    public void teardown() {
        for (MultiThreadedClientServiceCache<?> cache : _caches) {
            cache.close();
        }
    }

    @Test (expected = NullPointerException.class)
    public void testCheckOutFromNullEndPoint()
            throws Exception {
        newCache().checkOut(null);
    }

    @Test (expected = NullPointerException.class)
    public void testCheckInNullHandle()
            throws Exception {
        newCache().checkIn(null);
    }

    @Test (expected = NullPointerException.class)
    public void testCheckInToNullEndPoint()
            throws Exception {
        Service service = mock(Service.class);
        ServiceHandle<Service> handle = new ServiceHandle<>(service, null);
        newCache().checkIn(handle);
    }

    @Test (expected = NullPointerException.class)
    public void testCheckInNullServiceInstance()
            throws Exception {
        ServiceHandle<Service> handle = new ServiceHandle<>(null, _endPoint);
        newCache().checkIn(handle);
    }

    @Test (expected = NullPointerException.class)
    public void testEvictNullEndPoint() {
        newCache().evict(null);
    }

    @Test
    public void testFactoryExceptionIsPropagated() {
        NullPointerException exception = mock(NullPointerException.class);
        when(_factory.create(any(ServiceEndPoint.class))).thenThrow(exception);

        try {
            newCache().register(_endPoint);
            fail();
        } catch (Exception caught) {
            assertSame(exception, caught);
        }
    }

    @Test
    public void testServiceInstancesAreIsReused()
            throws Exception {
        Service service = mock(Service.class);
        when(_factory.create(any(ServiceEndPoint.class))).thenReturn(service);

        MultiThreadedClientServiceCache<Service> cache = newCache();
        ServiceHandle<Service> handle1 = cache.checkOut(_endPoint);
        ServiceHandle<Service> handle2 = cache.checkOut(_endPoint);
        ServiceHandle<Service> handle3 = cache.checkOut(_endPoint);
        ServiceHandle<Service> handle4 = cache.checkOut(_endPoint);


        assertSame(service, handle1.getService());
        assertSame(service, handle2.getService());
        assertSame(service, handle3.getService());
        assertSame(service, handle4.getService());

        assertEquals(1, cache.getNumActiveInstances(_endPoint));
        assertEquals(1, cache.getNumIdleInstances(_endPoint));
    }

    @Test
    public void testEvictedEndPointDestroyedManualEviction()
            throws Exception {
        MultiThreadedClientServiceCache<Service> cache = newCache(0);
        ServiceHandle<Service> handle = cache.checkOut(_endPoint);
        cache.checkIn(handle);
        cache.evict(_endPoint);

        verify(_factory).destroy(_endPoint, handle.getService());
    }

    @Test
    public void testEvictedEndPointWhileServiceInstanceCheckedOut()
            throws Exception {
        Service validService = mock(Service.class);
        ServiceEndPoint validEndPoint = newEndPoint("valid", "name");
        when(_factory.create(validEndPoint)).thenReturn(validService);

        Service invalidService = mock(Service.class);
        ServiceEndPoint invalidEndPoint = newEndPoint("invalid", "name");
        when(_factory.create(invalidEndPoint)).thenReturn(invalidService);

        MultiThreadedClientServiceCache<Service> cache = newCache(0);
        cache.register(invalidEndPoint);
        cache.register(validEndPoint);

        ServiceHandle<Service> invalidHandle = cache.checkOut(invalidEndPoint);
        ServiceHandle<Service> validHandle = cache.checkOut(validEndPoint);

        cache.evict(invalidEndPoint);

        cache.checkIn(invalidHandle);
        cache.checkIn(validHandle);

        verify(_factory, never()).destroy(validEndPoint, validService);
        verify(_factory, times(1)).destroy(invalidEndPoint, invalidService);
    }

    @Test
    public void testEvictedEndPointWhileDuplicateServiceInstancesCheckedOut()
            throws Exception {
        Service service = mock(Service.class);
        when(_factory.create(any(ServiceEndPoint.class))).thenReturn(service);

        MultiThreadedClientServiceCache<Service> cache = newCache(0);

        ServiceHandle<Service> handle1 = cache.checkOut(_endPoint);
        ServiceHandle<Service> handle2 = cache.checkOut(_endPoint);

        cache.evict(_endPoint);

        cache.checkIn(handle1);
        cache.checkIn(handle2);

        assertSame(service, handle1.getService());
        assertSame(service, handle2.getService());
        verify(_factory, times(1)).destroy(_endPoint, service);
    }

    @Test (expected = NullPointerException.class)
    public void testNumIdleNullEndPoint() {
        MultiThreadedClientServiceCache<Service> cache = newCache();
        cache.getNumIdleInstances(null);
    }

    @Test (expected = NullPointerException.class)
    public void testNumActiveNullEndPoint() {
        MultiThreadedClientServiceCache<Service> cache = newCache();
        cache.getNumActiveInstances(null);
    }

    @Test
    public void testNumActiveInstances()
            throws Exception {
        MultiThreadedClientServiceCache<Service> cache = newCache();
        cache.register(_endPoint);
        assertEquals(1, cache.getNumActiveInstances(_endPoint));
        assertEquals(0, cache.getNumActiveInstances(newEndPoint("new", "endPoint")));
    }

    @Test
    public void testNumIdleInstances()
            throws Exception {
        MultiThreadedClientServiceCache<Service> cache = newCache();
        cache.register(_endPoint);
        assertEquals(1, cache.getNumIdleInstances(_endPoint));
        assertEquals(0, cache.getNumActiveInstances(newEndPoint("new", "endPoint")));
    }

    @Test
    public void testCloseDestroysCachedInstances()
            throws Exception {
        MultiThreadedClientServiceCache<Service> cache = newCache();
        ServiceHandle<Service> handle = cache.checkOut(_endPoint);
        cache.checkIn(handle);
        cache.close();

        verify(_factory).destroy(_endPoint, handle.getService());
    }


    @Test
    public void testMultipleClose() {
        MultiThreadedClientServiceCache<Service> cache = newCache();
        cache.close();
        cache.close();
    }

    private MultiThreadedClientServiceCache<Service> newCache() {
        return newCache(1);
    }

    private MultiThreadedClientServiceCache<Service> newCache(int ttl) {
        MultiThreadedClientServiceCache<Service> cache = new MultiThreadedClientServiceCache<>(_factory, Executors.newSingleThreadScheduledExecutor(), ttl, ttl, _metricRegistry);
        _caches.add(cache);
        return cache;
    }

    private ServiceEndPoint newEndPoint(String id, String name) {
        ServiceEndPoint endPoint = mock(ServiceEndPoint.class);
        when(endPoint.getId()).thenReturn(id);
        when(endPoint.getServiceName()).thenReturn(name);
        when(endPoint.getPayload()).thenReturn("");
        return endPoint;
    }

    public static interface Service {
    }
}
