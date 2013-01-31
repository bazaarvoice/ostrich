package com.bazaarvoice.ostrich.retry;

import com.bazaarvoice.ostrich.RetryPolicy;

import static com.google.common.base.Preconditions.checkArgument;

public abstract class SleepingRetry implements RetryPolicy {
    private final int _maxNumAttempts;

    protected SleepingRetry(int maxNumAttempts) {
        checkArgument(maxNumAttempts >= 0);
        _maxNumAttempts = maxNumAttempts;
    }

    @Override
    public boolean allowRetry(int numAttempts, long elapsedTimeMs) {
        checkArgument(numAttempts >= 1);
        if (numAttempts >= _maxNumAttempts) {
            return false;
        }

        try {
            Thread.sleep(getSleepTimeMs(numAttempts, elapsedTimeMs));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }

        return true;
    }

    protected abstract long getSleepTimeMs(int numAttempts, long elapsedTimeMs);
}
