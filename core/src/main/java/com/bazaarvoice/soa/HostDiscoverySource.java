package com.bazaarvoice.soa;

/**
 * Source for {@link HostDiscovery} instances.
 */
public interface HostDiscoverySource {
    /**
     * Returns a {@link HostDiscovery} instance for the specified service or {@code null} if this source is not
     * configured to know about the service.  It is the caller's responsibility to close the returned instance.
     *
     * @param serviceName The name of the service.
     * @return a {@link HostDiscovery} instance for the specified service.
     */
    HostDiscovery forService(String serviceName);
}
