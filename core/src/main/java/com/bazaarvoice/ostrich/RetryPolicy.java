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

/** Abstracts the strategy of determining when to retry operations. */
public interface RetryPolicy {
    /**
     * Called when an operation has failed for some reason.  If this method returns <code>true</code>
     * then the operation will be retried.
     *
     * @param numAttempts   The number of attempts that have happened so far.  This must be greater than zero.
     * @param elapsedTimeMs The amount of time in milliseconds that the operation has been attempted.
     * @return <code>true</code> if the operation can be tried again, <code>false</code> otherwise.
     */
    boolean allowRetry(int numAttempts, long elapsedTimeMs);
}
