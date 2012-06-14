package com.bazaarvoice.soa.exceptions;

public abstract class DiscoveryException extends RuntimeException {
    public DiscoveryException() {
        super();
    }

    public DiscoveryException(String message) {
        super(message);
    }

    public DiscoveryException(String message, Throwable cause) {
        super(message, cause);
    }

    public DiscoveryException(Throwable cause) {
        super(cause);
    }
}
