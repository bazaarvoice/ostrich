package com.bazaarvoice.soa.registry;

import com.bazaarvoice.soa.ServiceEndPoint;
import com.bazaarvoice.soa.ServiceEndPointJsonCodec;
import com.bazaarvoice.soa.ServiceRegistry;
import com.bazaarvoice.soa.internal.CuratorConnection;
import com.bazaarvoice.soa.zookeeper.ZooKeeperConnection;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Charsets;
import com.google.common.collect.Maps;
import com.netflix.curator.framework.CuratorFramework;
import com.netflix.curator.utils.ZKPaths;
import org.apache.zookeeper.CreateMode;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

/**
 * A <code>ServiceRegistry</code> implementation that uses ZooKeeper as its backing data store.
 */
public class ZooKeeperServiceRegistry implements ServiceRegistry
{
    /** The root path in ZooKeeper for where service registrations are stored. */
    @VisibleForTesting
    static final String ROOT_SERVICES_PATH = "services";

    /** Maximum number of bytes that can be stored on a node in ZooKeeper. */
    @VisibleForTesting
    static final int MAX_DATA_SIZE = 1024 * 1024;

    // All dates are represented in ISO-8601 format and in the UTC time zone.
    @VisibleForTesting
    static final DateTimeFormatter ISO8601 = ISODateTimeFormat.dateTime().withZoneUTC();

    private final CuratorFramework _curator;
    private final AtomicBoolean _closed = new AtomicBoolean(false);

    /** The ephemeral data that's been written to ZooKeeper.  Saved in case the connection is lost and then regained. */
    private final Map<String, ZooKeeperPersistentEphemeralNode> _nodes = Maps.newConcurrentMap();

    public ZooKeeperServiceRegistry(ZooKeeperConnection connection) {
        this(((CuratorConnection) checkNotNull(connection)).getCurator());
    }

    @VisibleForTesting
    ZooKeeperServiceRegistry(CuratorFramework curator) {
        checkNotNull(curator);
        checkArgument(curator.isStarted());
        _curator = curator;
    }

    /** {@inheritDoc} */
    @Override
    public void register(ServiceEndPoint endpoint) {
        register(endpoint, true);
    }

    @VisibleForTesting
    void register(ServiceEndPoint endpoint, boolean includeRegistrationTime) {
        checkState(!_closed.get());
        checkNotNull(endpoint);

        Map<String, Object> registrationData = Maps.newHashMap();
        if (includeRegistrationTime) {
            DateTime now = DateTime.now().toDateTime(DateTimeZone.UTC);
            registrationData.put("registration-time", ISO8601.print(now));
        }

        byte[] data = ServiceEndPointJsonCodec.toJson(endpoint, registrationData).getBytes(Charsets.UTF_8);
        checkState(data.length < MAX_DATA_SIZE, "Serialized form of ServiceEndpoint must be < 1MB.");

        String path = makeEndpointPath(endpoint);
        _nodes.put(path, new ZooKeeperPersistentEphemeralNode(_curator, path, data, CreateMode.EPHEMERAL));
    }

    /** {@inheritDoc} */
    @Override
    public void unregister(ServiceEndPoint endpoint) {
        checkState(!_closed.get());
        checkNotNull(endpoint);

        String path = makeEndpointPath(endpoint);
        ZooKeeperPersistentEphemeralNode node = _nodes.remove(path);
        if (node != null) {
            node.close(10, TimeUnit.SECONDS);
        }
    }

    @Override
    public void close() throws IOException {
        if (!_closed.compareAndSet(false, true)) {
            // Already closed
            return;
        }

        for (ZooKeeperPersistentEphemeralNode node : _nodes.values()) {
            node.close(10, TimeUnit.SECONDS);
        }
        _nodes.clear();
    }

    /** Return the curator instance used by this registry. */
    @VisibleForTesting
    CuratorFramework getCurator() {
        return _curator;
    }

    @VisibleForTesting
    String getRegisteredEndpointPath(ServiceEndPoint endpoint) {
        String path = makeEndpointPath(endpoint);
        ZooKeeperPersistentEphemeralNode node = _nodes.get(path);
        return (node != null) ? node.getActualPath() : null;
    }

    /**
     * Construct the path in ZooKeeper to where a service's children live.
     * @param serviceName The name of the service to get the ZooKeeper path for.
     * @return The ZooKeeper path.
     */
    public static String makeServicePath(String serviceName) {
        checkNotNull(serviceName);
        checkArgument(!"".equals(serviceName));
        return ZKPaths.makePath(ROOT_SERVICES_PATH, serviceName);
    }

    /**
     * Convert a <code>ServiceEndpoint</code> into the path in ZooKeeper where it will be registered.
     * @param endpoint The service endpoint to get the ZooKeeper path for.
     * @return The ZooKeeper path.
     */
    private static String makeEndpointPath(ServiceEndPoint endpoint) {
        String servicePath = makeServicePath(endpoint.getServiceName());
        String id = endpoint.getId();
        return ZKPaths.makePath(servicePath, id);
    }
}
