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
 * A registry for services.  The <code>ServiceRegistry</code> gives service providers a way to register their service
 * end points in order to make them available to consumers of the service across multiple JVMs.
 */
public interface ServiceRegistry extends Closeable {
    /**
     * Add an end point of a service to the service registry and make it available for discovery.
     *
     * @param endPoint The end point of the service to register.
     * @throws RuntimeException If there was a problem registering the end point.
     */
    void register(ServiceEndPoint endPoint);

    /**
     * Remove an end point of a service from the service registry.  This will make it no longer available
     * to be discovered.
     *
     * @param endPoint The end point of the service to unregister.
     * @throws RuntimeException If there was a problem de-registering the end point.
     */
    void unregister(ServiceEndPoint endPoint);
}
