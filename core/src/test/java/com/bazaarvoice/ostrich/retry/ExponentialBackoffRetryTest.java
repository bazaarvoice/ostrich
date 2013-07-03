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
package com.bazaarvoice.ostrich.retry;

import com.bazaarvoice.ostrich.RetryPolicy;
import org.junit.Assert;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ExponentialBackoffRetryTest {
    @Test(expected = IllegalArgumentException.class)
    public void testNegativeNumberOfTimes() {
        new ExponentialBackoffRetry(-1, 1, 1, TimeUnit.MILLISECONDS);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNegativeBaseSleepTime() {
        new ExponentialBackoffRetry(1, -1, 1, TimeUnit.MILLISECONDS);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNegativeMaxSleepTime() {
        new ExponentialBackoffRetry(1, 1, -1, TimeUnit.MILLISECONDS);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testZeroNAttempts() {
        RetryPolicy retry = new ExponentialBackoffRetry(0, 0, 0, TimeUnit.MILLISECONDS);
        retry.allowRetry(0, 0);
    }

    @Test
    public void testRetryMaxZeroAttempts() {
        RetryPolicy retry = new ExponentialBackoffRetry(0, 0, 0, TimeUnit.MILLISECONDS);
        assertFalse(retry.allowRetry(1, 0));
    }

    @Test
    public void testRetryMaxOneAttempt() {
        RetryPolicy retry = new ExponentialBackoffRetry(1, 0, 0, TimeUnit.MILLISECONDS);
        assertFalse(retry.allowRetry(1, 0));
    }

    @Test
    public void testRetryMaxTwoAttempts() {
        RetryPolicy retry = new ExponentialBackoffRetry(2, 0, 0, TimeUnit.MILLISECONDS);
        assertTrue(retry.allowRetry(1, 0));
        assertFalse(retry.allowRetry(2, 0));
    }

    @Test
    public void testBaseSleepTimeUnits() {
        SleepingRetry retry = new ExponentialBackoffRetry(10, 20, 1000, TimeUnit.SECONDS);

        long sleepTimeMs = retry.getSleepTimeMs(1, 0);
        assertBetween(TimeUnit.SECONDS.toMillis(20), sleepTimeMs, TimeUnit.SECONDS.toMillis(40));
    }

    @Test
    public void testMaxSleepTimeUnits() {
        SleepingRetry retry = new ExponentialBackoffRetry(10, 50, 20, TimeUnit.SECONDS);

        long sleepTimeMs = retry.getSleepTimeMs(1, 0);
        assertBetween(TimeUnit.SECONDS.toMillis(10), sleepTimeMs, TimeUnit.SECONDS.toMillis(20));
    }

    @Test
    public void testFirstRetrySleepTime() {
        SleepingRetry retry = new ExponentialBackoffRetry(10, 20, 1000, TimeUnit.MILLISECONDS);

        long sleepTimeMs = retry.getSleepTimeMs(1, 0);
        assertBetween(20, sleepTimeMs, 40);
    }

    @Test
    public void testSecondRetrySleepTime() {
        SleepingRetry retry = new ExponentialBackoffRetry(10, 20, 1000, TimeUnit.MILLISECONDS);

        long sleepTimeMs = retry.getSleepTimeMs(2, 0);
        assertBetween(40, sleepTimeMs, 80);
    }

    @Test
    public void testRetryBaseMoreThanMaxSleepTime() {
        SleepingRetry retry = new ExponentialBackoffRetry(10, 60, 50, TimeUnit.MILLISECONDS);

        long sleepTimeMs = retry.getSleepTimeMs(1, 0);
        Assert.assertEquals(50, sleepTimeMs);
    }

    @Test
    public void testMinRetrySleepTime() {
        SleepingRetry retry = new ExponentialBackoffRetry(10, 10, 50, TimeUnit.MILLISECONDS);
        for (int i = 1; i <= 10; i++) {
            long sleepTimeMs = retry.getSleepTimeMs(i, 0);
            assertTrue(sleepTimeMs >= Math.min(10 * (1 << (i - 1)), 25));
        }
    }

    @Test
    public void testMaxRetrySleepTime() {
        SleepingRetry retry = new ExponentialBackoffRetry(10, 10, 50, TimeUnit.MILLISECONDS);
        for (int i = 1; i <= 10; i++) {
            long sleepTimeMs = retry.getSleepTimeMs(i, 0);
            assertTrue(sleepTimeMs <= 50);
        }
    }

    /** Asserts expectedLowerBound <= actual <= expectedUpperBound. */
    private void assertBetween(long expectedLowerBound, long actual, long expectedUpperBound) {
        assertTrue("Expected: " + expectedLowerBound + " <= " + actual, expectedLowerBound <= actual);
        assertTrue("Expected: " + expectedUpperBound + " >= " + actual, expectedUpperBound >= actual);
    }
}
