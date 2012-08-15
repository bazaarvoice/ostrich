package com.bazaarvoice.soa.healthcheck;

import com.bazaarvoice.soa.HealthCheckResult;
import com.google.common.collect.Iterables;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class DefaultHealthCheckResultsTest {
    private static final HealthCheckResult HEALTHY = mock(HealthCheckResult.class);
    private static final HealthCheckResult UNHEALTHY = mock(HealthCheckResult.class);

    private final DefaultHealthCheckResults _results = new DefaultHealthCheckResults();

    @Before
    public void setup() {
        when(HEALTHY.isHealthy()).thenReturn(true);
        when(UNHEALTHY.isHealthy()).thenReturn(false);
    }

    @Test
    public void testAllResultsStartEmpty() {
        assertTrue(Iterables.isEmpty(_results.getAllResults()));
    }

    @Test
    public void testAllResultsContainsHealthy() {
        _results.addHealthCheckResult(HEALTHY);

        assertTrue(Iterables.contains(_results.getAllResults(), HEALTHY));
    }

    @Test
    public void testAllResultsContainsUnhealthy() {
        _results.addHealthCheckResult(UNHEALTHY);

        assertTrue(Iterables.contains(_results.getAllResults(), UNHEALTHY));
    }

    @Test
    public void testHealthyResultStartsNull() {
        assertNull(_results.getHealthyResult());
    }

    @Test
    public void testHealthyResultContainsHealthy() {
        _results.addHealthCheckResult(HEALTHY);

        assertSame(HEALTHY, _results.getHealthyResult());
    }

    @Test
    public void testHealthyResultsDoesNotContainUnhealthy() {
        _results.addHealthCheckResult(UNHEALTHY);

        assertNull(_results.getHealthyResult());
    }

    @Test
    public void testUnhealthyResultsStartEmpty() {
        assertTrue(Iterables.isEmpty(_results.getUnhealthyResults()));
    }

    @Test
    public void testUnhealthyResultsContainUnhealthy() {
        _results.addHealthCheckResult(UNHEALTHY);

        assertTrue(Iterables.contains(_results.getUnhealthyResults(), UNHEALTHY));
    }

    @Test
    public void testUnhealthyResultsDoesNotContainHealthy() {
        _results.addHealthCheckResult(HEALTHY);

        assertFalse(Iterables.contains(_results.getUnhealthyResults(), HEALTHY));
    }

    @Test
    public void testHasHealthyResultStartsFalse() {
        assertFalse(_results.hasHealthyResult());
    }

    @Test
    public void testHasHealthyResultTrue() {
        _results.addHealthCheckResult(HEALTHY);

        assertTrue(_results.hasHealthyResult());
    }

    @Test
    public void testHasHealthyResultFalse() {
        _results.addHealthCheckResult(UNHEALTHY);

        assertFalse(_results.hasHealthyResult());
    }

    @Test(expected = NullPointerException.class)
    public void testNullResult() {
        _results.addHealthCheckResult(null);
    }
}
