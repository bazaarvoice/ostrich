package com.bazaarvoice.soa.pool;

import org.junit.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;

public class ServiceCachingPolicyBuilderTest {
    @Test
    public void testCacheExhaustionActionSet() {
        ServiceCachingPolicyBuilder builder = new ServiceCachingPolicyBuilder();
        builder.withCacheExhaustionAction(ServiceCachingPolicy.ExhaustionAction.GROW);
        assertEquals(builder.build().getCacheExhaustionAction(), ServiceCachingPolicy.ExhaustionAction.GROW);
    }
    
    @Test
    public void testMaxNumServiceInstancesSet() {
        ServiceCachingPolicyBuilder builder = new ServiceCachingPolicyBuilder();
        builder.withMaxNumServiceInstances(1);
        assertEquals(builder.build().getMaxNumServiceInstances(), 1);
    }
    
    @Test
    public void testMaxNumServiceInstancesPerEndPointSet() {
        ServiceCachingPolicyBuilder builder = new ServiceCachingPolicyBuilder();
        builder.withMaxNumServiceInstancesPerEndPoint(1);
        assertEquals(builder.build().getMaxNumServiceInstancesPerEndPoint(), 1);
    }
    
    @Test
    public void testMinIdleTimeBeforeEvictionSet() {
        ServiceCachingPolicyBuilder builder = new ServiceCachingPolicyBuilder();
        builder.withMaxServiceInstanceIdleTime(10, TimeUnit.SECONDS);
        assertEquals(builder.build().getMaxServiceInstanceIdleTime(TimeUnit.SECONDS), 10);
    }

    @Test(expected = NullPointerException.class)
    public void testNullExhaustionAction() {
        ServiceCachingPolicyBuilder builder = new ServiceCachingPolicyBuilder();
        builder.withCacheExhaustionAction(null);
        builder.build();
    }

    @Test(expected = IllegalStateException.class)
    public void testInvalidMaxNumServiceInstances() {
        ServiceCachingPolicyBuilder builder = new ServiceCachingPolicyBuilder();
        builder.withMaxNumServiceInstances(-1);
        builder.build();
    }

    @Test(expected = IllegalStateException.class)
    public void testInvalidMaxNumServiceInstancesPerEndPoint() {
        ServiceCachingPolicyBuilder builder = new ServiceCachingPolicyBuilder();
        builder.withMaxNumServiceInstancesPerEndPoint(-1);
        builder.build();
    }

    @Test(expected = IllegalStateException.class)
    public void testInvalidMaxServiceInstanceIdleTime() {
        ServiceCachingPolicyBuilder builder = new ServiceCachingPolicyBuilder();
        builder.withMaxServiceInstanceIdleTime(0, TimeUnit.MILLISECONDS);
        builder.build();
    }
}
