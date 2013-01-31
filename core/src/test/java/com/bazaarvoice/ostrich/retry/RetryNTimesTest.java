package com.bazaarvoice.ostrich.retry;

import com.bazaarvoice.ostrich.RetryPolicy;
import org.junit.Test;

import java.util.Random;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RetryNTimesTest {
    @Test(expected = IllegalArgumentException.class)
    public void testNegativeNumberOfTimes() {
        new RetryNTimes(-1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNegativeSleepTime() {
        new RetryNTimes(1, -1, TimeUnit.MILLISECONDS);
    }

    @Test
    public void testPositiveSleepTime() {
        RetryNTimes retry = new RetryNTimes(1, 123, TimeUnit.MILLISECONDS);
        assertEquals(123, retry.getSleepTimeMs(1, 0));
        assertEquals(123, retry.getSleepTimeMs(1, 1));
    }

    @Test
    public void testPositiveSleepUnits() {
        RetryNTimes retry = new RetryNTimes(1, 123, TimeUnit.SECONDS);
        assertEquals(TimeUnit.SECONDS.toMillis(123), retry.getSleepTimeMs(1, 0));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testZeroNAttempts() {
        RetryPolicy retry = new RetryNTimes(0);
        retry.allowRetry(0, 0);
    }

    @Test
    public void testRetryMaxZeroAttempts() {
        RetryPolicy retry = new RetryNTimes(0);
        assertFalse(retry.allowRetry(1, 0));
    }

    @Test
    public void testRetryMaxOneAttempt() {
        RetryPolicy retry = new RetryNTimes(1);
        assertFalse(retry.allowRetry(1, 0));
    }

    @Test
    public void testRetryMaxTwoAttempts() {
        RetryPolicy retry = new RetryNTimes(2);
        assertTrue(retry.allowRetry(1, 0));
        assertFalse(retry.allowRetry(2, 0));
    }

    @Test
    public void testRetryRandomTimes() {
        Random rnd = new Random();
        int N = rnd.nextInt(1000) + 1;

        RetryPolicy retry = new RetryNTimes(N);
        for(int i = 1; i < N; i++) {
            assertTrue(retry.allowRetry(i, 0));
        }
        assertFalse(retry.allowRetry(N, 0));
    }
}
