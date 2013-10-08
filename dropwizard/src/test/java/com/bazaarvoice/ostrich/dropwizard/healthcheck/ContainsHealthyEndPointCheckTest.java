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
package com.bazaarvoice.ostrich.dropwizard.healthcheck;

import com.bazaarvoice.ostrich.HealthCheckResult;
import com.bazaarvoice.ostrich.HealthCheckResults;
import com.bazaarvoice.ostrich.ServicePool;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.yammer.metrics.core.HealthCheck;
import org.junit.Before;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.Collections;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
public class ContainsHealthyEndPointCheckTest {
    private static final HealthCheckResult HEALTHY = mock(HealthCheckResult.class);
    private static final HealthCheckResult UNHEALTHY = mock(HealthCheckResult.class);

    private final ServicePool<Service> _pool = mock(ServicePool.class);
    private final HealthCheckResults _results = mock(HealthCheckResults.class);

    private HealthCheck _check;

    @Before
    public void setup() {
        when(HEALTHY.isHealthy()).thenReturn(true);
        when(UNHEALTHY.isHealthy()).thenReturn(false);

        // Default to empty results.
        when(_pool.checkForHealthyEndPoint()).thenReturn(_results);
        when(_results.getHealthyResult()).thenReturn(null);
        when(_results.getUnhealthyResults()).thenReturn(Collections.<HealthCheckResult>emptyList());
        when(_results.hasHealthyResult()).thenAnswer(new Answer<Boolean>() {
            @Override
            public Boolean answer(InvocationOnMock invocationOnMock) throws Throwable {
                return _results.getHealthyResult() != null;
            }
        });
        when(_results.getAllResults()).thenAnswer(new Answer<Iterable<HealthCheckResult>>() {
            @Override
            public Iterable<HealthCheckResult> answer(InvocationOnMock invocationOnMock) throws Throwable {
                return Iterables.concat(ImmutableList.of(_results.getHealthyResult()), _results.getUnhealthyResults());
            }
        });

        _check = ContainsHealthyEndPointCheck.forPool(_pool, "test");
    }

    @Test(expected = NullPointerException.class)
    public void testNullPool() {
        ContainsHealthyEndPointCheck.forPool(null, "test");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNullServiceName() {
        ContainsHealthyEndPointCheck.forPool(_pool, null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testEmptyServiceName() {
        ContainsHealthyEndPointCheck.forPool(_pool, "");
    }

    @Test
    public void testEmptyResult() {
        assertFalse(_check.execute().isHealthy());
    }

    @Test
    public void testOnlyUnhealthyResult() {
        when(_results.getUnhealthyResults()).thenReturn(ImmutableList.of(UNHEALTHY));

        assertFalse(_check.execute().isHealthy());
    }

    @Test
    public void testOnlyHealthyResult() {
        when(_results.getHealthyResult()).thenReturn(HEALTHY);

        assertTrue(_check.execute().isHealthy());
    }

    @Test
    public void testBothResults() {
        when(_results.getHealthyResult()).thenReturn(HEALTHY);
        when(_results.getUnhealthyResults()).thenReturn(ImmutableList.of(UNHEALTHY));

        assertTrue(_check.execute().isHealthy());
    }

    interface Service {}
}
