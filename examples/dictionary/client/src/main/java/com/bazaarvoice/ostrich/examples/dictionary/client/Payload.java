package com.bazaarvoice.ostrich.examples.dictionary.client;

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
    private final WordRange _partition;

    public static Payload valueOf(String string) {
        Map<?, ?> map = JsonHelper.fromJson(string, Map.class);
        URI serviceUri = URI.create((String) checkNotNull(map.get("url"), "url"));
        URI adminUri = URI.create((String) checkNotNull(map.get("adminUrl"), "adminUrl"));
        WordRange partition = new WordRange((String) checkNotNull(map.get("partition"), "partition"));
        return new Payload(serviceUri, adminUri, partition);
    }

    public Payload(URI serviceUrl, URI adminUrl, WordRange partition) {
        _serviceUrl = checkNotNull(serviceUrl, "serviceUrl");
        _adminUrl = checkNotNull(adminUrl, "adminUrl");
        _partition = checkNotNull(partition, "partition");
    }

    public URI getServiceUrl() {
        return _serviceUrl;
    }

    public URI getAdminUrl() {
        return _adminUrl;
    }

    public WordRange getPartition() {
        return _partition;
    }
}
