package com.bazaarvoice.soa.exceptions;

public class MaxRetriesException extends DiscoveryException {
    public MaxRetriesException() {
        super();
    }

    public MaxRetriesException(String message) {
        super(message);
    }

    public MaxRetriesException(String message, Throwable cause) {
        super(message, cause);
    }

    public MaxRetriesException(Throwable cause) {
        super(cause);
    }
}
