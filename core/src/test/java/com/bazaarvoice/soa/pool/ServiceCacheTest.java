package com.bazaarvoice.soa.pool;

import com.bazaarvoice.soa.ServiceCallback;
import com.bazaarvoice.soa.ServiceEndPoint;
import com.bazaarvoice.soa.ServiceFactory;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.fail;
import static org.mockito.Matchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ServiceCacheTest {

    private static final Service FOO_SERVICE = mock(Service.class);
    private static final Service BAR_SERVICE = mock(Service.class);
    private static final Service BAZ_SERVICE = mock(Service.class);
    private static final ServiceEndPoint END_POINT = mock(ServiceEndPoint.class);

    private ServiceCallback<Service, Void> _callback;
    private ServiceFactory<Service> _factory;

    @SuppressWarnings("unchecked")
    @Before
    public void setup() {
        _factory = mock(ServiceFactory.class);
        when(_factory.create(END_POINT)).thenReturn(FOO_SERVICE).thenReturn(BAR_SERVICE).thenReturn(BAZ_SERVICE);
        _callback = mock(ServiceCallback.class);
    }

    @Test
    public void testServiceIsCached() {
        ServiceCachingPolicy policy = new ServiceCachingPolicy(100, 10, TimeUnit.SECONDS, 1);
        ServiceCache<Service> cache = new ServiceCache<Service>(_factory, policy);
        cache.call(_callback, END_POINT);
        cache.call(_callback, END_POINT);
        verify(_factory, times(1)).create(END_POINT);
        verify(_callback, times(2)).call(same(FOO_SERVICE));
    }

    @Test
    public void testInUseCachedServiceNotReused() {
        ServiceCachingPolicy policy = new ServiceCachingPolicy(100, 10, TimeUnit.SECONDS, 1);
        ServiceCache<Service> cache = new ServiceCache<Service>(_factory, policy);
        cache.checkOut(END_POINT);
        cache.call(_callback, END_POINT);
        verify(_factory, times(2)).create(END_POINT);
        verify(_callback, times(1)).call(same(BAR_SERVICE));
    }

    @Test
    public void testRemovedEndPointEvicted() {
        ServiceCachingPolicy policy = new ServiceCachingPolicy(100, 10, TimeUnit.SECONDS, 1);
        ServiceCache<Service> cache = new ServiceCache<Service>(_factory, policy);
        cache.call(_callback, END_POINT);
        cache.endPointRemoved(END_POINT);
        cache.call(_callback, END_POINT);
        verify(_callback, times(1)).call(same(FOO_SERVICE));
        verify(_callback, times(1)).call(same(BAR_SERVICE));
    }

    @Test
    public void testTimedOutServiceEvicted() {
        ServiceCachingPolicy policy = new ServiceCachingPolicy(100, 1, TimeUnit.SECONDS, 1);
        ServiceCache<Service> cache = new ServiceCache<Service>(_factory, policy);
        cache.call(_callback, END_POINT);
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            fail();
        }
        cache.call(_callback, END_POINT);
        verify(_callback, times(1)).call(same(FOO_SERVICE));
        verify(_callback, times(1)).call(same(BAR_SERVICE));
    }

    @Test
    public void testExcessiveServicesNotCached() {
        ServiceCachingPolicy policy = new ServiceCachingPolicy(100, 10, TimeUnit.SECONDS, 1);
        ServiceCache<Service> cache = new ServiceCache<Service>(_factory, policy);
        cache.checkOut(END_POINT);
        cache.call(_callback, END_POINT);
        cache.call(_callback, END_POINT);
        verify(_factory, times(3)).create(END_POINT);
        verify(_callback, times(1)).call(same(BAR_SERVICE));
        verify(_callback, times(1)).call(same(BAZ_SERVICE));
    }

    private static interface Service {
    }
}
