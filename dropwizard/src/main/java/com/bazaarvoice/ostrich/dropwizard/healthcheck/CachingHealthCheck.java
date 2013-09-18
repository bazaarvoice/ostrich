package com.bazaarvoice.ostrich.dropwizard.healthcheck;

import com.yammer.metrics.core.HealthCheck;
import org.joda.time.DateTime;
import org.joda.time.Duration;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A health check that wraps an existing health check, caches the result, and guarantees that
 * only a single thread will execute {@code check()} after the cached results have become stale.  Utilizing this
 * health check will prevent overloading your services, even if run excessively.
 */
public class CachingHealthCheck extends HealthCheck {
    private final HealthCheck _healthCheck;
    private final Duration _minTTL;
    private final Duration _maxTTL;
    private final Lock _lock = new ReentrantLock();
    private volatile CachedResult _cachedResult = null;

    /**
     * Constructs a cached health check that will show as healthy if the provided health check shows as healthy,
     * and will attempt to update the result after 60 seconds, declaring cached result stale after 90 seconds.
     * @param healthCheck The {@code HealthCheck} to call and cache results of.
     */
    public CachingHealthCheck(HealthCheck healthCheck) {
        this(healthCheck, Duration.standardSeconds(60), Duration.standardSeconds(90));
    }

    /**
     * Constructs a cached health check that will show as healthy if the provided health check shows as healthy,
     * and will attempt to update the result after {@code minTTL} seconds, declaring cached result stale after {@code maxTTL} seconds.
     * @param healthCheck The {@code HealthCheck} to call and cache results of.
     * @param minTTL The {@code Duration} to wait before attempting to refresh the result by executing {@code healthCheck.check()}
     * @param maxTTL The {@code Duration} to wait before declaring a cached result stale.
     */
    public CachingHealthCheck(HealthCheck healthCheck, Duration minTTL, Duration maxTTL) {
        super(healthCheck.getName());
        _healthCheck = healthCheck;
        _minTTL = minTTL;
        _maxTTL = maxTTL;
    }

    @Override
    protected Result check() throws Exception {
        CachedResult cachedResult = _cachedResult;  // Only read the volatile once

        // Check to see if the data is young enough to not be updated.  If so, just return it as-is.
        if (cachedResult != null && isYoungEnough(cachedResult)) {
            return cachedResult.getResult();
        }

        if (cachedResult != null && isNotExpired(cachedResult)) {
            // The data is present, and isn't yet expired.  See if anyone is updating it.
            if (!_lock.tryLock()) {
                // It's safe to return, we don't have the lock, and someone else is updating the data.
                return cachedResult.getResult();
            }
        }

        // We either need to compute a new cachedResult, or we need to wait for the current updater to finish
        // computing the cachedResult.
        _lock.lock();

        //
        // NOTE: If control reaches this line, then we have acquired the lock.  We MUST unlock prior to returning.
        //
        try {
            // It's possible that we blocked prior to getting the lock, re-read the cachedResult.
            cachedResult = _cachedResult;

            // Check to make sure that the cachedResult wasn't updated while we were blocked and is now young enough.
            if (cachedResult != null && isYoungEnough(cachedResult)) {
                return cachedResult.getResult();
            }

            // We have to compute the new cachedResult.
            Result result = _healthCheck.execute();
            _cachedResult = new CachedResult(now(), result);

            return result;
        } finally {
            _lock.unlock();
        }
    }

    private boolean isYoungEnough(CachedResult cachedResult) {
        return now().isBefore(cachedResult.getTime().plus(_minTTL));
    }

    private boolean isNotExpired(CachedResult cachedResult) {
        return now().isBefore(cachedResult.getTime().plus(_maxTTL));
    }

    private DateTime now() {
        return new DateTime();
    }

    class CachedResult {
        public final DateTime _time;
        public final Result _result;

        public CachedResult(DateTime time, Result result) {
            this._time = time;
            this._result = result;
        }

        DateTime getTime() {
            return _time;
        }

        Result getResult() {
            return _result;
        }
    }
}
