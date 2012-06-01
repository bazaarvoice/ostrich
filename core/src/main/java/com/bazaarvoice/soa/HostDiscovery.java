package com.bazaarvoice.soa;

/**
 * The <code>HostDiscovery</code> class is used to encapsulate the strategy that provides a set of hosts for use.
 * Users of this class shouldn't cache the results of discovery as subclasses can choose to change the set of available
 * hosts based on some external mechanism (ex. using ZooKeeper).
 */
public interface HostDiscovery {
    /**
     * Retrieve the available hosts.
     *
     * @return The available hosts.
     */
    Iterable<ServiceEndpoint> getHosts();

    /**
     * Returns true if the specified endpoint is a member of the set returned by {@link #getHosts()}.
     *
     * @param endpoint The endpoint to test.
     * @return True if the specified endpoint is a member of the set returned by {@link #getHosts()}.
     */
    boolean contains(ServiceEndpoint endpoint);

    /**
     * Add an endpoint listener.
     *
     * @param listener The endpoint listener to add.
     */
    void addListener(EndpointListener listener);

    /**
     * Remove an endpoint listener.
     *
     * @param listener The endpoint listener to remove.
     */
    void removeListener(EndpointListener listener);

    /** Listener interface that is notified when endpoints are added and removed. */
    interface EndpointListener {
        void onEndpointAdded(ServiceEndpoint endpoint);
        void onEndpointRemoved(ServiceEndpoint endpoint);
    }
}
