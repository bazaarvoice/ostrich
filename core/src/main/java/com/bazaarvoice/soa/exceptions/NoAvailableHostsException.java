package com.bazaarvoice.soa.exceptions;

public class NoAvailableHostsException extends DiscoveryException {
    public NoAvailableHostsException() {
        super();
    }

    public NoAvailableHostsException(String message) {
        super(message);
    }

    public NoAvailableHostsException(String message, Throwable cause) {
        super(message, cause);
    }

    public NoAvailableHostsException(Throwable cause) {
        super(cause);
    }
}
