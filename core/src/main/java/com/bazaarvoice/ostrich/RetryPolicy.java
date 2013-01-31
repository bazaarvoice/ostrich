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
