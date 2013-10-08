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
package com.bazaarvoice.ostrich.examples.calculator.client;

import java.net.URI;
import java.util.Map;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * SOA (Ostrich) payload object, typically embedded within a {@link com.bazaarvoice.ostrich.ServiceEndPoint}.
 * <p>
 * Dropwizard web servers expose a service URL (typically port 8080) which is the main RESTful end point plus they
 * expose an administration URL (typically port 8081) which is used for health checks by the SOA load balancing
 * algorithms.
 */
public class Payload {
    private final URI _serviceUrl;
    private final URI _adminUrl;

    public static Payload valueOf(String string) {
        Map<?, ?> map = JsonHelper.fromJson(string, Map.class);
        URI serviceUri = URI.create((String) checkNotNull(map.get("url"), "url"));
        URI adminUri = URI.create((String) checkNotNull(map.get("adminUrl"), "adminUrl"));
        return new Payload(serviceUri, adminUri);
    }

    public Payload(URI serviceUrl, URI adminUrl) {
        _serviceUrl = checkNotNull(serviceUrl, "serviceUrl");
        _adminUrl = checkNotNull(adminUrl, "adminUrl");
    }

    public URI getServiceUrl() {
        return _serviceUrl;
    }

    public URI getAdminUrl() {
        return _adminUrl;
    }
}
