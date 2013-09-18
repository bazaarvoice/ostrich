package com.bazaarvoice.ostrich.dropwizard.healthcheck;

import com.bazaarvoice.ostrich.ServicePool;
import com.yammer.metrics.core.HealthCheck;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
public class ContainsValidEndPointCheckTest {
    private final ServicePool<Service> _pool = mock(ServicePool.class);

    private HealthCheck _check;

    @Before
    public void setup() {
        _check = ContainsValidEndPointCheck.forPool(_pool, "test");
    }

    @Test (expected = NullPointerException.class)
    public void testNullPool() {
        ContainsValidEndPointCheck.forPool(null, "test");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNullServiceName() {
        ContainsValidEndPointCheck.forPool(_pool, null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testEmptyServiceName() {
        ContainsValidEndPointCheck.forPool(_pool, "");
    }

    @Test
    public void testEmptyResult() {
        when(_pool.getNumValidEndPoints()).thenReturn(0);
        when(_pool.getNumBadEndPoints()).thenReturn(0);

        assertFalse(_check.execute().isHealthy());
    }

    @Test
    public void testOnlyUnhealthyResult() {
        when(_pool.getNumValidEndPoints()).thenReturn(0);
        when(_pool.getNumBadEndPoints()).thenReturn(2);

        assertFalse(_check.execute().isHealthy());
    }

    @Test
    public void testOnlyHealthyResult() {
        when(_pool.getNumValidEndPoints()).thenReturn(2);
        when(_pool.getNumBadEndPoints()).thenReturn(0);

        assertTrue(_check.execute().isHealthy());
    }

    @Test
    public void testBothResults() {
        when(_pool.getNumValidEndPoints()).thenReturn(1);
        when(_pool.getNumBadEndPoints()).thenReturn(1);

        assertTrue(_check.execute().isHealthy());
    }

    interface Service {}
}
