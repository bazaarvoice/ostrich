package com.bazaarvoice.ostrich.exceptions;

public abstract class DiscoveryException extends ServiceException {
    private static final long serialVersionUID = 0;

    protected DiscoveryException() {
        super();
    }

    protected DiscoveryException(String message) {
        super(message);
    }

    protected DiscoveryException(String message, Throwable cause) {
        super(message, cause);
    }

    protected DiscoveryException(Throwable cause) {
        super(cause);
    }
}
