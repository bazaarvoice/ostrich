package com.bazaarvoice.ostrich.discovery;

import com.bazaarvoice.ostrich.HostDiscovery;
import com.bazaarvoice.ostrich.HostDiscoverySource;
import com.bazaarvoice.ostrich.ServiceEndPoint;
import com.bazaarvoice.ostrich.ServiceEndPointBuilder;
import com.google.common.collect.Lists;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Convenience class for configuring FixedHostDiscovery objects in Dropwizard.
 * <p>
 * To make this directly configurable via a Dropwizard YAML configuration file, subclass this class, provide a concrete
 * Payload class implementation that the Dropwizard YAML parser can use to construct the Map constructor argument, and
 * mark the single argument constructor with @JsonCreator.
 */
public class ConfiguredFixedHostDiscoverySource<Payload> implements HostDiscoverySource {
    /** Map of end point id to payload data. */
    private final Map<String, Payload> _endPoints;

    /**
     * Creates an empty {@link HostDiscoverySource} that always returns {@code null}, causing the service pool builder
     * to try the next {@code HostDiscoverySource} (typically ZooKeeper).
     */
    public ConfiguredFixedHostDiscoverySource() {
        this(Collections.<String, Payload>emptyMap());
    }

    /**
     * Creates a {@link HostDiscoverySource} that, if the map is non-empty, can override other host discovery sources
     * such as ZooKeeper with a fixed set of end points.
     */
    public ConfiguredFixedHostDiscoverySource(Map<String, Payload> endPoints) {
        _endPoints = checkNotNull(endPoints);
    }

    @Override
    public HostDiscovery forService(String serviceName) {
        if (_endPoints.isEmpty()) {
            return null;
        }

        List<ServiceEndPoint> endPoints = Lists.newArrayListWithCapacity(_endPoints.size());
        for (Map.Entry<String, Payload> entry : _endPoints.entrySet()) {
            String id = entry.getKey();
            Payload payload = entry.getValue();
            endPoints.add(new ServiceEndPointBuilder()
                    .withServiceName(serviceName)
                    .withId(id)
                    .withPayload(serialize(serviceName, id, payload))
                    .build());
        }
        return new FixedHostDiscovery(endPoints);
    }

    /**
     * Subclasses may override this to customize the persistent format of the payload.
     */
    @SuppressWarnings("UnusedParameters")
    protected String serialize(String serviceName, String id, Payload payload) {
        return String.valueOf(payload);
    }

    @Override
    public String toString() {
        return _endPoints.keySet().toString();
    }
}
