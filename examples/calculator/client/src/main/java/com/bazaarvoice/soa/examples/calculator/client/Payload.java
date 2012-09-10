package com.bazaarvoice.soa.examples.calculator.client;

import java.net.URI;
import java.util.Map;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * SOA (Ostrich) payload object, typically embedded within a {@link com.bazaarvoice.soa.ServiceEndPoint}.
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
