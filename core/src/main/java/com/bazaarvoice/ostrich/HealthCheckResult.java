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
