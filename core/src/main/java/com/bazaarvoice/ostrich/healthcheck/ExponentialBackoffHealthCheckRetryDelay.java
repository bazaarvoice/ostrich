package com.bazaarvoice.ostrich.healthcheck;

import com.bazaarvoice.ostrich.HealthCheckResult;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Policy that results in a bounded, exponentially increasing delay between healthchecks.
 *
 * The delay interval is randomized to avoid the "thundering herd" effect when there are many
 * clients all attempting to retry at the same time.
 */
public class ExponentialBackoffHealthCheckRetryDelay implements HealthCheckRetryDelay {
    private final long _baseDelay;
    private final long _maxDelayMillis;

    public ExponentialBackoffHealthCheckRetryDelay(long baseDelay, long maxDelay, TimeUnit unit) {
        checkArgument(baseDelay >= 0);
        checkArgument(maxDelay >= baseDelay);
        checkNotNull(unit);

        _baseDelay = unit.toMillis(baseDelay);
        _maxDelayMillis = unit.toMillis(maxDelay);
    }

    @Override
    public long getDelay(int numAttempts, HealthCheckResult result) {
        checkArgument(numAttempts >= 1);
        checkNotNull(result);

        // Pick a random number between baseDelay*2^(n-1) and baseDelay*2^n (inclusive), subject
        // to the upper bound.
        long rangeMax = Math.min(_baseDelay * (1 << numAttempts), _maxDelayMillis);
        long rangeMin = rangeMax / 2;

        return rangeMin + ThreadLocalRandom.current().nextLong(rangeMax - rangeMin + 1);
    }
}
