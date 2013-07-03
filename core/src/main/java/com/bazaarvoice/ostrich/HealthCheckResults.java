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
package com.bazaarvoice.ostrich;

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
