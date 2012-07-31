package com.bazaarvoice.soa.exceptions;

/**
 * An exception indicating a {@link com.bazaarvoice.soa.LoadBalanceAlgorithm} chose {@code null} rather than one of the
 * provided end points.
 */
public class NoSuitableHostsException extends DiscoveryException {
    private static final long serialVersionUID = 0;
}
