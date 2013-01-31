package com.bazaarvoice.ostrich.exceptions;

/**
 * An exception to be thrown when something has been tried unsuccessfully until a
 * {@link com.bazaarvoice.ostrich.RetryPolicy} no longer allows retries.
 */
public class MaxRetriesException extends ServiceException {
    private static final long serialVersionUID = 0;
}
