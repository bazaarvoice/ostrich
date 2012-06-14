package com.bazaarvoice.soa.exceptions;

public class NoSuitableHostsException extends DiscoveryException {
    public NoSuitableHostsException() {
        super();
    }

    public NoSuitableHostsException(String message) {
        super(message);
    }

    public NoSuitableHostsException(String message, Throwable cause) {
        super(message, cause);
    }

    public NoSuitableHostsException(Throwable cause) {
        super(cause);
    }
}
