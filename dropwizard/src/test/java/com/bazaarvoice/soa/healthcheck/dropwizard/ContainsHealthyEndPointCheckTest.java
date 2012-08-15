package com.bazaarvoice.soa.healthcheck.dropwizard;

import com.bazaarvoice.soa.HealthCheckResults;
import com.bazaarvoice.soa.HealthCheckResult;
import com.bazaarvoice.soa.ServicePool;
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

public class ContainsHealthyEndPointCheckTest {
    private static final HealthCheckResult HEALTHY = mock(HealthCheckResult.class);
    private static final HealthCheckResult UNHEALTHY = mock(HealthCheckResult.class);

    private final String _name = "test";
    @SuppressWarnings("unchecked") private final ServicePool<Service> _pool = mock(ServicePool.class);
    private final HealthCheckResults _results = mock(HealthCheckResults.class);


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
    }

    @Test(expected = NullPointerException.class)
    public void testNullPool() {
        new ContainsHealthyEndPointCheck(null, _name);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNullServiceName() {
        new ContainsHealthyEndPointCheck(_pool, null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testEmptyServiceName() {
        new ContainsHealthyEndPointCheck(_pool, "");
    }

    @Test
    public void testEmptyResult() {
        HealthCheck check = new ContainsHealthyEndPointCheck(_pool, _name);

        assertFalse(check.execute().isHealthy());
    }

    @Test
    public void testOnlyUnhealthyResult() {
        when(_results.getUnhealthyResults()).thenReturn(ImmutableList.of(UNHEALTHY));

        HealthCheck check = new ContainsHealthyEndPointCheck(_pool, _name);

        assertFalse(check.execute().isHealthy());
    }

    @Test
    public void testOnlyHealthyResult() {
        when(_results.getHealthyResult()).thenReturn(HEALTHY);

        HealthCheck check = new ContainsHealthyEndPointCheck(_pool, _name);

        assertTrue(check.execute().isHealthy());
    }

    @Test
    public void testBothResults() {
        when(_results.getHealthyResult()).thenReturn(HEALTHY);
        when(_results.getUnhealthyResults()).thenReturn(ImmutableList.of(UNHEALTHY));

        HealthCheck check = new ContainsHealthyEndPointCheck(_pool, _name);

        assertTrue(check.execute().isHealthy());
    }

    interface Service {}
}
