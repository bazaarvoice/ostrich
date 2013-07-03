/*
 * Copyright 2013 Bazaarvoice, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
