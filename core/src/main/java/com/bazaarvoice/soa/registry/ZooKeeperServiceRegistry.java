package com.bazaarvoice.soa.registry;

import com.bazaarvoice.soa.ServiceEndPoint;
import com.bazaarvoice.soa.ServiceEndPointJsonCodec;
import com.bazaarvoice.soa.ServiceRegistry;
import com.bazaarvoice.soa.metrics.Metrics;
import com.bazaarvoice.zookeeper.ZooKeeperConnection;
import com.bazaarvoice.zookeeper.recipes.ZooKeeperPersistentEphemeralNode;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Charsets;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.Maps;
import com.netflix.curator.utils.ZKPaths;
import com.yammer.metrics.core.Counter;
import org.apache.zookeeper.CreateMode;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;

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
    static final String ROOT_SERVICES_PATH = "ostrich";

    /** Maximum number of bytes that can be stored on a node in ZooKeeper. */
    @VisibleForTesting
    static final int MAX_DATA_SIZE = 1024 * 1024;

    // All dates are represented in ISO-8601 format and in the UTC time zone.
    @VisibleForTesting
    static final DateTimeFormatter ISO8601 = ISODateTimeFormat.dateTime().withZoneUTC();

    private final NodeFactory _nodeFactory;
    private volatile boolean _closed = false;

    /** The ephemeral data that's been written to ZooKeeper.  Saved in case the connection is lost and then regained. */
    private final Map<String, ZooKeeperPersistentEphemeralNode> _nodes = Maps.newConcurrentMap();

    private final Metrics _metrics = new Metrics(ZooKeeperServiceRegistry.class);
    private final LoadingCache<String, Counter> _numRegisteredEndpoints = CacheBuilder.newBuilder()
            .build(new CacheLoader<String, Counter>() {
                @Override
                public Counter load(String scope) throws Exception {
                    return _metrics.newCounter(scope, "num-registered-end-points");
                }
            });

    public ZooKeeperServiceRegistry(ZooKeeperConnection connection) {
        this(new NodeFactory(connection));
    }

    @VisibleForTesting
    ZooKeeperServiceRegistry(NodeFactory nodeFactory) {
        _nodeFactory = checkNotNull(nodeFactory);
    }

    /** {@inheritDoc} */
    @Override
    public void register(ServiceEndPoint endPoint) {
        register(endPoint, true);
    }

    @VisibleForTesting
    void register(ServiceEndPoint endPoint, boolean includeRegistrationTime) {
        checkState(!_closed);
        checkNotNull(endPoint);

        Map<String, Object> registrationData = Maps.newHashMap();
        if (includeRegistrationTime) {
            DateTime now = DateTime.now().toDateTime(DateTimeZone.UTC);
            registrationData.put("registration-time", ISO8601.print(now));
        }

        byte[] data = ServiceEndPointJsonCodec.toJson(endPoint, registrationData).getBytes(Charsets.UTF_8);
        checkState(data.length < MAX_DATA_SIZE, "Serialized form of ServiceEndPoint must be < 1MB.");

        String path = makeEndPointPath(endPoint);
        ZooKeeperPersistentEphemeralNode oldNode = _nodes.put(path, _nodeFactory.create(path, data));
        if (oldNode != null) {
            closeNode(oldNode);
        }

        String serviceName = endPoint.getServiceName();
        _numRegisteredEndpoints.getUnchecked(serviceName).inc();
    }

    /** {@inheritDoc} */
    @Override
    public void unregister(ServiceEndPoint endPoint) {
        checkState(!_closed);
        checkNotNull(endPoint);

        String path = makeEndPointPath(endPoint);
        ZooKeeperPersistentEphemeralNode node = _nodes.remove(path);
        if (node != null) {
            closeNode(node);

            String serviceName = endPoint.getServiceName();
            _numRegisteredEndpoints.getUnchecked(serviceName).dec();
        }
    }

    @Override
    public synchronized void close() throws IOException {
        if (_closed) {
            return;
        }

        _closed = true;

        for (ZooKeeperPersistentEphemeralNode node : _nodes.values()) {
            closeNode(node);
        }
        _nodes.clear();
        _metrics.close();
    }

    private void closeNode(ZooKeeperPersistentEphemeralNode node) {
        node.close(10, TimeUnit.SECONDS);
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
     * Convert a <code>ServiceEndPoint</code> into the path in ZooKeeper where it will be registered.
     * @param endPoint The service end point to get the ZooKeeper path for.
     * @return The ZooKeeper path.
     */
    @VisibleForTesting
    static String makeEndPointPath(ServiceEndPoint endPoint) {
        String servicePath = makeServicePath(endPoint.getServiceName());
        String id = endPoint.getId();
        return ZKPaths.makePath(servicePath, id);
    }

    @VisibleForTesting
    static class NodeFactory {
        private final ZooKeeperConnection _connection;

        NodeFactory(ZooKeeperConnection connection) {
            _connection = checkNotNull(connection);
        }

        ZooKeeperPersistentEphemeralNode create(String path, byte[] data) {
            return new ZooKeeperPersistentEphemeralNode(_connection, path, data, CreateMode.EPHEMERAL);
        }
    }
}
