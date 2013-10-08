/*
 * Copyright 2013 Bazaarvoice, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.bazaarvoice.ostrich.pool;

import org.junit.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;

public class ServiceCachingPolicyBuilderTest {
    @Test
    public void testCacheExhaustionActionSet() {
        ServiceCachingPolicyBuilder builder = new ServiceCachingPolicyBuilder();
        builder.withCacheExhaustionAction(ServiceCachingPolicy.ExhaustionAction.GROW);

        assertEquals(ServiceCachingPolicy.ExhaustionAction.GROW, builder.build().getCacheExhaustionAction());
    }
    
    @Test
    public void testMaxNumServiceInstancesSet() {
        ServiceCachingPolicyBuilder builder = new ServiceCachingPolicyBuilder();
        builder.withMaxNumServiceInstances(1);

        assertEquals(1, builder.build().getMaxNumServiceInstances());
    }
    
    @Test
    public void testMaxNumServiceInstancesPerEndPointSet() {
        ServiceCachingPolicyBuilder builder = new ServiceCachingPolicyBuilder();
        builder.withMaxNumServiceInstancesPerEndPoint(1);

        assertEquals(1, builder.build().getMaxNumServiceInstancesPerEndPoint());
    }
    
    @Test
    public void testMinIdleTimeBeforeEvictionSet() {
        ServiceCachingPolicyBuilder builder = new ServiceCachingPolicyBuilder();
        builder.withMaxServiceInstanceIdleTime(10, TimeUnit.SECONDS);

        assertEquals(10, builder.build().getMaxServiceInstanceIdleTime(TimeUnit.SECONDS));
    }

    @Test(expected = NullPointerException.class)
    public void testNullExhaustionAction() {
        ServiceCachingPolicyBuilder builder = new ServiceCachingPolicyBuilder();
        builder.withCacheExhaustionAction(null);
    }

    @Test(expected = IllegalStateException.class)
    public void testInvalidMaxNumServiceInstances() {
        ServiceCachingPolicyBuilder builder = new ServiceCachingPolicyBuilder();
        builder.withMaxNumServiceInstances(-1);
    }

    @Test(expected = IllegalStateException.class)
    public void testInvalidMaxNumServiceInstancesPerEndPoint() {
        ServiceCachingPolicyBuilder builder = new ServiceCachingPolicyBuilder();
        builder.withMaxNumServiceInstancesPerEndPoint(-1);
    }

    @Test(expected = IllegalStateException.class)
    public void testInvalidMaxServiceInstanceIdleTime() {
        ServiceCachingPolicyBuilder builder = new ServiceCachingPolicyBuilder();
        builder.withMaxServiceInstanceIdleTime(0, TimeUnit.MILLISECONDS);
    }
}
