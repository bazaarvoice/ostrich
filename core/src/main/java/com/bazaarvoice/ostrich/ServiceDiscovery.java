package com.bazaarvoice.ostrich;

import java.io.Closeable;

/**
 * The {@code ServiceDiscovery} interface is used to encapsulate the strategy that provides a picture of what ostrich
 * services are available.  Users of this class shouldn't cache the results of discovery as subclasses can choose to
 * change the set of available services based on some external mechanism (ex. using ZooKeeper).
 */
public interface ServiceDiscovery extends Closeable {
    /**
     * Retrieve the available services.
     *
     * @return The available services.
     */
    Iterable<String> getServices();
}
