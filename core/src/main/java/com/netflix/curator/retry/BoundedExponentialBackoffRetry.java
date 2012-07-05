package com.netflix.curator.retry;

/**
 * Retry policy that retries a set number of times with an increasing (up to a maximum) sleep time between retries.
 *
 * NOTE: This class is just temporary.  A version of it has been submitted to the Curator folks for inclusion in
 * Curator proper.  <a href="https://github.com/Netflix/curator/pull/100">Pull request.</a>
 */
public class BoundedExponentialBackoffRetry extends ExponentialBackoffRetry {
    private final int maxSleepTimeMs;

    public BoundedExponentialBackoffRetry(int baseSleepTimeMs, int maxSleepTimeMs, int maxRetries) {
        super(baseSleepTimeMs, maxRetries);
        this.maxSleepTimeMs = maxSleepTimeMs;
    }

    protected int getSleepTimeMs(int retryCount, long elapsedTimeMs) {
        return Math.min(maxSleepTimeMs, super.getSleepTimeMs(retryCount, elapsedTimeMs));
    }
}
