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

import com.bazaarvoice.ostrich.RetryPolicy;

import static com.google.common.base.Preconditions.checkArgument;

public abstract class SleepingRetry implements RetryPolicy {
    private final int _maxNumAttempts;

    protected SleepingRetry(int maxNumAttempts) {
        checkArgument(maxNumAttempts >= 0);
        _maxNumAttempts = maxNumAttempts;
    }

    @Override
    public boolean allowRetry(int numAttempts, long elapsedTimeMs) {
        checkArgument(numAttempts >= 1);
        if (numAttempts >= _maxNumAttempts) {
            return false;
        }

        try {
            Thread.sleep(getSleepTimeMs(numAttempts, elapsedTimeMs));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }

        return true;
    }

    protected abstract long getSleepTimeMs(int numAttempts, long elapsedTimeMs);
}
