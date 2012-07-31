package com.bazaarvoice.soa.exceptions;

/**
 * An exception to be thrown when something has been tried unsuccessfully until a
 * {@link com.bazaarvoice.soa.RetryPolicy} no longer allows retries.
 */
public class MaxRetriesException extends ServiceException {
    private static final long serialVersionUID = 0;
}
