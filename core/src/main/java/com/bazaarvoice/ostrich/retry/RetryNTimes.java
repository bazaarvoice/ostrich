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
package com.bazaarvoice.ostrich.retry;

import java.util.concurrent.TimeUnit;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/** A retry policy that permits a fixed number of attempts at executing an operation. */
public class RetryNTimes extends SleepingRetry {
    private final long _sleepTimeBetweenAttemptsMillis;

    /**
     * Attempt the operation at most {@code maxNumAttempts} times without any delay between each attempt.
     * @param maxNumAttempts The maximum number of attempts.  This is equal one plus the maximum number of retries.
     *                       This should be greater than zero.  For backward compatibility zero is accepted, but one
     *                       attempt is always made.
     */
    public RetryNTimes(int maxNumAttempts) {
        this(maxNumAttempts, 0, TimeUnit.MILLISECONDS);
    }

    /**
     * Attempt the operation at most {@code maxNumAttempts} times, sleeping the specified time duration before each
     * retry attempt.
     * @param maxNumAttempts The maximum number of attempts.  This is equal one plus the maximum number of retries.
     *                       This should be greater than zero.  For backward compatibility zero is accepted, but one
     *                       attempt is always made.
     * @param sleepTimeBetweenRetries The amount of time to sleep before each retry attempt.  If zero, there will be no
     *                                delay between attempts.
     * @param unit The units (milliseconds, seconds, etc.) of {@code sleepTimeBetweenRetries}.
     */
    public RetryNTimes(int maxNumAttempts, long sleepTimeBetweenRetries, TimeUnit unit) {
        super(maxNumAttempts);

        checkArgument(sleepTimeBetweenRetries >= 0);
        checkNotNull(unit);
        _sleepTimeBetweenAttemptsMillis = unit.toMillis(sleepTimeBetweenRetries);
    }

    @Override
    protected long getSleepTimeMs(int numAttempts, long elapsedTimeMs) {
        return _sleepTimeBetweenAttemptsMillis;
    }
}
