package com.bazaarvoice.soa;

/**
 * Source for {@link HostDiscovery} instances.
 */
public interface HostDiscoverySource {
    /**
     * Returns a {@link HostDiscovery} instance for the specified service or {@code null} if this source is not
     * configured to know about the service.  It is the caller's responsibility to close the returned instance.
     *
     * @param ensembleName The name of the group of servers providing the service.
     * @param serviceType The type of the service.
     * @return a {@link HostDiscovery} instance for the specified service.
     */
    HostDiscovery forService(String ensembleName, String serviceType);
}
