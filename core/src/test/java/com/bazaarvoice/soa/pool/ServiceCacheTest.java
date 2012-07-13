package com.bazaarvoice.soa.pool;

import com.bazaarvoice.soa.ServiceEndPoint;
import com.bazaarvoice.soa.ServiceFactory;
import com.bazaarvoice.soa.exceptions.InvalidEndPointCheckOutAttemptException;
import com.bazaarvoice.soa.exceptions.NoCachedConnectionAvailableException;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import org.apache.commons.pool.impl.GenericKeyedObjectPool;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Matchers;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import javax.annotation.Nullable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.mockStatic;

@RunWith (PowerMockRunner.class)
@PrepareForTest ({GenericKeyedObjectPool.class})
public class ServiceCacheTest {

    private static final Service FOO_SERVICE = mock(Service.class);
    private static final Service BAR_SERVICE = mock(Service.class);
    private static final Service BAZ_SERVICE = mock(Service.class);
    private static final ServiceEndPoint END_POINT = mock(ServiceEndPoint.class);

    private ServiceFactory<Service> _factory;
    private Predicate<ServiceEndPoint> _predicate;

    @SuppressWarnings("unchecked")
    @Before
    public void setup() {
        _factory = mock(ServiceFactory.class);
        when(_factory.create(END_POINT)).thenReturn(FOO_SERVICE, BAR_SERVICE, BAZ_SERVICE);
        _predicate = mock(Predicate.class);
        when(_predicate.apply(Matchers.<ServiceEndPoint>any())).thenReturn(true);
    }

    @Test
    public void testServiceIsCached() {
        final ServiceCachingPolicy policy = new ServiceCachingPolicy(100, 1, 10, 5, TimeUnit.SECONDS);
        final ServiceCache<Service> cache = new ServiceCache<Service>(_factory, policy, _predicate);
        final Service first = cache.checkOut(END_POINT);
        cache.checkIn(END_POINT, first);
        final Service second = cache.checkOut(END_POINT);
        cache.close();
        assertSame(first, second);
    }

    @Test
    public void testInUseCachedServiceNotReused() {
        final ServiceCachingPolicy policy = new ServiceCachingPolicy(100, 2, 10, 5, TimeUnit.SECONDS);
        final ServiceCache<Service> cache = new ServiceCache<Service>(_factory, policy, _predicate);
        final Service first = cache.checkOut(END_POINT);
        final Service second = cache.checkOut(END_POINT);
        cache.close();
        assertNotSame(first, second);
    }

    @Test
    public void testRemovedEndPointEvicted() {
        final ServiceCachingPolicy policy = new ServiceCachingPolicy(100, 2, 10, 5, TimeUnit.SECONDS);
        final ServiceCache<Service> cache = new ServiceCache<Service>(_factory, policy, _predicate);
        final Service first = cache.checkOut(END_POINT);
        cache.checkIn(END_POINT, first);
        cache.endPointRemoved(END_POINT);
        final Service second = cache.checkOut(END_POINT);
        cache.close();
        assertNotSame(first, second);
    }

    @Test
    public void testInvalidatedEndPointEvicted() {
        final ServiceCachingPolicy policy = new ServiceCachingPolicy(100, 2, 10, 5, TimeUnit.SECONDS);
        final AtomicBoolean valid = new AtomicBoolean(true);

        ServiceCache<Service> cache = new ServiceCache<Service>(_factory, policy, new Predicate<ServiceEndPoint>() {
            @Override
            public boolean apply(@Nullable ServiceEndPoint endPoint) {
                return valid.getAndSet(true);
            }
        });
        final Service first = cache.checkOut(END_POINT);
        cache.checkIn(END_POINT, first);
        valid.set(false);
        final Service second = cache.checkOut(END_POINT);
        cache.close();
        assertNotSame(first, second);
    }

    @Test(expected = InvalidEndPointCheckOutAttemptException.class)
    public void testInvalidEndPointsNotCached() {
        final Predicate<ServiceEndPoint> falsePredicate = Predicates.alwaysFalse();
        final ServiceCachingPolicy policy = new ServiceCachingPolicy(100, 1, 10, 5, TimeUnit.SECONDS);
        final ServiceCache<Service> cache = new ServiceCache<Service>(_factory, policy, falsePredicate);
        cache.checkOut(END_POINT);
    }

    @Test(expected = NoCachedConnectionAvailableException.class)
    public void testExcessiveServicesNotCached() {
        final ServiceCachingPolicy policy = new ServiceCachingPolicy(100, 1, 10, 5, TimeUnit.SECONDS);
        final ServiceCache<Service> cache = new ServiceCache<Service>(_factory, policy, _predicate);
        cache.checkOut(END_POINT);
        try {
            cache.checkOut(END_POINT);
        } finally {
            cache.close();
        }
    }

    @Test
    public void testExpiredServicesEvicted() throws Exception {
        final ServiceCachingPolicy policy = new ServiceCachingPolicy(100, 1, 1, 5, TimeUnit.SECONDS);
        final ServiceCache<Service> cache = new ServiceCache<Service>(_factory, policy, _predicate);

        // GenericKeyedObjectPool uses System.currentTimeMillis() to check eviction eligibility, so we need to control
        // that. Hooray for tests reliant on implementation details of implementation details!
        mockStatic(System.class);
        final DateTime time = DateTime.now();
        when(System.currentTimeMillis()).thenAnswer(new Answer<Long>() {
            private int numCalls = 0;

            @Override
            public Long answer(InvocationOnMock invocationOnMock)
                    throws Throwable {
                // Increment time by 2 seconds each time System.currentTimeMillis is called.
                return time.plusSeconds(numCalls++ * 2).getMillis();
            }
        });

        final Service first = cache.checkOut(END_POINT);
        cache.checkIn(END_POINT, first);
        cache.evict();
        final Service second = cache.checkOut(END_POINT);
        cache.close();
        assertNotSame(first, second);
    }

    @Test
    public void testNonExpiredServicesNotEvicted() throws Exception {
        final ServiceCachingPolicy policy = new ServiceCachingPolicy(100, 1, 1, 5, TimeUnit.SECONDS);
        final ServiceCache<Service> cache = new ServiceCache<Service>(_factory, policy, _predicate);

        // GenericKeyedObjectPool uses System.currentTimeMillis() to check eviction eligibility, so we need to control
        // that. Hooray for tests reliant on implementation details of implementation details!
        mockStatic(System.class);
        final DateTime time = DateTime.now();
        when(System.currentTimeMillis()).thenReturn(time.getMillis());

        final Service first = cache.checkOut(END_POINT);
        cache.checkIn(END_POINT, first);
        cache.evict();
        final Service second = cache.checkOut(END_POINT);
        cache.close();
        assertSame(first, second);
    }

    public static interface Service {
    }
}