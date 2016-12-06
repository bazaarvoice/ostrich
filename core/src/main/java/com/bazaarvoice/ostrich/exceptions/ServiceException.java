package com.bazaarvoice.ostrich.exceptions;

/**
 * A general exception thrown when execution callback {@link com.bazaarvoice.ostrich.ServiceCallback}
 * for an endpoint failed.
 * See {@link com.bazaarvoice.ostrich.exceptions} package for more specific exceptions.
 */
public class ServiceException extends RuntimeException {
    private static final long serialVersionUID = 0;

    public ServiceException() {
        super();
    }

    public ServiceException(String message) {
        super(message);
    }

    public ServiceException(String message, Throwable cause) {
        super(message, cause);
    }

    public ServiceException(Throwable cause) {
        super(cause);
    }
}