package com.bazaarvoice.ostrich.healthcheck;

import com.bazaarvoice.ostrich.HealthCheckResult;

/**
 * Determines delay period for end point health check requests.
 */
public interface HealthCheckRetryDelay {
    /**
     * Get the delay for next health check attempt for an end point.
     *
     * @param numAttempts number of health checks that have been attempted for the end point (&gt;= 1)
     * @param result      most recent health check failure result
     * @return milliseconds to wait for next health check attempt
     */
    long getDelay(int numAttempts, HealthCheckResult result);
}
