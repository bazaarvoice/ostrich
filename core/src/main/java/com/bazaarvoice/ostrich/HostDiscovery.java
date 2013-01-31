package com.bazaarvoice.ostrich;

import java.io.Closeable;

/**
 * The <code>HostDiscovery</code> class is used to encapsulate the strategy that provides a set of hosts for use.
 * Users of this class shouldn't cache the results of discovery as subclasses can choose to change the set of available
 * hosts based on some external mechanism (ex. using ZooKeeper).
 */
public interface HostDiscovery extends Closeable {
    /**
     * Retrieve the available hosts.
     *
     * @return The available hosts.
     */
    Iterable<ServiceEndPoint> getHosts();

    /**
     * Returns true if the specified end point is a member of the set returned by {@link #getHosts()}.
     *
     * @param endPoint The end point to test.
     * @return True if the specified end point is a member of the set returned by {@link #getHosts()}.
     */
    boolean contains(ServiceEndPoint endPoint);

    /**
     * Add an end point listener.
     *
     * @param listener The end point listener to add.
     */
    void addListener(EndPointListener listener);

    /**
     * Remove an end point listener.
     *
     * @param listener The end point listener to remove.
     */
    void removeListener(EndPointListener listener);

    /** Listener interface that is notified when end points are added and removed. */
    interface EndPointListener {
        void onEndPointAdded(ServiceEndPoint endPoint);
        void onEndPointRemoved(ServiceEndPoint endPoint);
    }
}
