package com.bazaarvoice.ostrich.exceptions;

/**
 * An exception indicating that {@link com.bazaarvoice.ostrich.HostDiscovery} provided no end points.
 */
public class NoAvailableHostsException extends DiscoveryException {
    private static final long serialVersionUID = 0;

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
