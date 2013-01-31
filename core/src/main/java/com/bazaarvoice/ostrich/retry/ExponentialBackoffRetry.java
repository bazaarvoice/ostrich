package com.bazaarvoice.ostrich.retry;

import java.util.Random;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * A retry policy that permits a fixed number of attempts at executing an operation.  After each attempt the
 * application will delay for a bounded exponentially increasing time period before trying again.  The actual delay
 * interval is randomized to avoid the "thundering herd" effect when there are many clients all attempting to retry
 * at the same time.
 */
public class ExponentialBackoffRetry extends SleepingRetry {
    private final Random _random = new Random();
    private final long _baseSleepTimeMs;
    private final long _maxSleepTimeMs;

    /**
     * Attempt the operation at most {@code maxNumAttempts} times, sleeping a bounded exponentially increasing time
     * duration after each failed attempt.
     * @param maxNumAttempts The maximum number of attempts.  This is equal one plus the maximum number of retries.
     *                       This should be greater than zero.  For backward compatibility zero is accepted, but one
     *                       attempt is always made.
     * @param baseSleepTime The base amount of time to sleep before each retry attempt.  Because the actual sleep time
     *                      is randomized to some degree, the first retry will sleep between {@code baseSleepTime} and
     *                      {@code 2 * baseSleepTime}, the second retry will sleep between {@code 2 * baseSleepTime} and
     *                      {@code 4 * baseSleepTime}, etc., with a maximum sleep time of {@code maxSleepTime}.
     *                      If zero, there will be no delay between attempts.
     * @param maxSleepTime The maximum amount of time to sleep before each retry attempt.
     * @param unit The units (milliseconds, seconds, etc.) of {@code baseSleepTime} and {@code maxSleepTime}.
     */
    public ExponentialBackoffRetry(int maxNumAttempts, long baseSleepTime, long maxSleepTime, TimeUnit unit) {
        super(maxNumAttempts);

        checkArgument(baseSleepTime >= 0);
        checkArgument(maxSleepTime >= 0);
        checkNotNull(unit);

        _baseSleepTimeMs = unit.toMillis(baseSleepTime);
        _maxSleepTimeMs = unit.toMillis(maxSleepTime);
    }

    @Override
    protected long getSleepTimeMs(int numAttempts, long elapsedTimeMs) {
        // numAttempts is 1-based so the first sleep time is between _baseSleepTimeMs and 2*_baseSleepTimeMs.
        checkArgument(numAttempts >= 1);

        // In general, pick a random number between baseSleep*2^(n-1) and baseSleep*2^n (inclusive), subject to the
        // configured upper bound (_maxSleepTimeMs) and lower bound (_baseSleepTimeMs).  Attempt to ensure
        // randomization is effective by setting the rangeMin to 1/2 of the rangeMax.
        long rangeMax = Math.min(_baseSleepTimeMs * (1 << numAttempts), _maxSleepTimeMs);
        long rangeMin = Math.max(rangeMax / 2, _baseSleepTimeMs);

        return rangeMin >= rangeMax ? rangeMax : rangeMin + _random.nextInt((int) (rangeMax - rangeMin + 1));
    }
}
