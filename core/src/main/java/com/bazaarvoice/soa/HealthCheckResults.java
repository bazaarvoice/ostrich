package com.bazaarvoice.soa;

/**
 * A container for multiple {@link HealthCheckResult}s with convenience methods to check if there is a healthy
 * result, and also return one. May be empty, in which case all methods will return empty {@link Iterable}s
 * or {@code null}.
 */
public interface HealthCheckResults {
    /**
     * @return {@code true} if there is a healthy result, {@code false} otherwise.
     */
    boolean hasHealthyResult();

    /**
     * @return All results in the aggregate, regardless of health.
     */
    Iterable<HealthCheckResult> getAllResults();

    /**
     * Returns a healthy result if {@link #hasHealthyResult} is {@code true}. Returns {@code null} when
     * {@code hasHealthyResult} is false. If there are multiple healthy results, there is no guarantee as to which gets
     * returned.
     * @return A result in the aggregate whose {@link HealthCheckResult#isHealthy} method returns {@code true}, or
     * {@code null} if there are none.
     */
    HealthCheckResult getHealthyResult();

    /**
     * @return Results in the aggregate whose {@link HealthCheckResult#isHealthy} method returns {@code false}.
     */
    Iterable<HealthCheckResult> getUnhealthyResults();
}
