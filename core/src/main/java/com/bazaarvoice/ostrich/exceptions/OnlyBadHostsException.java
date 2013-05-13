package com.bazaarvoice.ostrich.exceptions;

/**
 * An exception thrown when all available end points have been marked as bad.
 */
public class OnlyBadHostsException extends DiscoveryException {
    private static final long serialVersionUID = 0;

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
