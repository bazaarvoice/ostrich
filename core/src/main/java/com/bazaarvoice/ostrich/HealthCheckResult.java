package com.bazaarvoice.ostrich;

import java.util.concurrent.TimeUnit;

/**
 * The result of a health check on an end point.
 */
public interface HealthCheckResult {
    /**
     * @return {@code true} if result is from a healthy end point, {@code false} otherwise.
     */
    boolean isHealthy();

    /**
     * @return The ID of the end point this result is for.
     */
    String getEndPointId();

    /**
     * Gets the amount of time the health check took to run or until it failed.
     * @param unit The {@code TimeUnit} the response time should be in.
     * @return The execution time of the health check in the units specified.
     */
    long getResponseTime(TimeUnit unit);
}
