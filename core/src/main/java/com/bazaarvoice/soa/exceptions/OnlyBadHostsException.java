package com.bazaarvoice.soa.exceptions;

public class OnlyBadHostsException extends DiscoveryException {
    public OnlyBadHostsException() {
        super();
    }

    public OnlyBadHostsException(String message) {
        super(message);
    }

    public OnlyBadHostsException(String message, Throwable cause) {
        super(message, cause);
    }

    public OnlyBadHostsException(Throwable cause) {
        super(cause);
    }
}
