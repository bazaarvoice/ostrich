package com.bazaarvoice.ostrich.exceptions;

/**
 * An exception to be thrown when a service cache does not have an idle cached instance for an end point, it does not
 * have room to create a new one, and it is configured to fail when exhausted.
 */
public class NoCachedInstancesAvailableException extends ServiceException {
    private static final long serialVersionUID = 0;

    public NoCachedInstancesAvailableException() {
        super();
    }

    public NoCachedInstancesAvailableException(String message) {
        super(message);
    }

    public NoCachedInstancesAvailableException(String message, Throwable cause) {
        super(message, cause);
    }

    public NoCachedInstancesAvailableException(Throwable cause) {
        super(cause);
    }
}
