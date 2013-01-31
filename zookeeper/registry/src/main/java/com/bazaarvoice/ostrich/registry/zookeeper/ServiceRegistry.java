package com.bazaarvoice.ostrich.registry.zookeeper;

import com.bazaarvoice.curator.recipes.PersistentEphemeralNode;
import com.bazaarvoice.ostrich.ServiceEndPoint;
import com.bazaarvoice.ostrich.ServiceEndPointJsonCodec;
import com.bazaarvoice.ostrich.metrics.Metrics;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Charsets;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.Maps;
import com.netflix.curator.framework.CuratorFramework;
import com.netflix.curator.utils.ZKPaths;
import com.yammer.metrics.core.Counter;
import org.apache.zookeeper.CreateMode;

import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

/**
 * A <code>ServiceRegistry</code> implementation that uses ZooKeeper as its backing data store.
 */
public class ServiceRegistry implements com.bazaarvoice.ostrich.ServiceRegistry
{
    /**
     * The root path in ZooKeeper for where service registrations are stored.
     * <p/>
     * WARNING: Do not modify this without also modifying the ALL of the corresponding paths in the service registry,
     * host discovery, and service discovery classes!!!
     */
    @VisibleForTesting
    static final String ROOT_SERVICES_PATH = "/ostrich";

    /** Maximum number of bytes that can be stored in a node in ZooKeeper. */
    @VisibleForTesting
    static final int MAX_DATA_SIZE = 1024 * 1024;

    // All dates are represented in ISO-8601 format and in the UTC time zone.
    private static final TimeZone UTC = TimeZone.getTimeZone("UTC");
    private static final String ISO8601_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'";

    private final NodeFactory _nodeFactory;
    private volatile boolean _closed = false;

    /** The ephemeral data that's been written to ZooKeeper.  Saved in case the connection is lost and then regained. */
    private final Map<String, PersistentEphemeralNode> _nodes = Maps.newConcurrentMap();

    private final Metrics _metrics = Metrics.forClass(ServiceRegistry.class);
    private final LoadingCache<String, Counter> _numRegisteredEndpoints = CacheBuilder.newBuilder()
            .build(new CacheLoader<String, Counter>() {
                @Override
                public Counter load(String scope) throws Exception {
                    return _metrics.newCounter(scope, "num-registered-end-points");
                }
            });

    public ServiceRegistry(CuratorFramework curator) {
        this(new NodeFactory(curator));
    }

    @VisibleForTesting
    ServiceRegistry(NodeFactory nodeFactory) {
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
            DateFormat iso8601 = new SimpleDateFormat(ISO8601_FORMAT);
            iso8601.setTimeZone(UTC);

            Date now = new Date();
            registrationData.put("registration-time", iso8601.format(now));
        }

        byte[] data = ServiceEndPointJsonCodec.toJson(endPoint, registrationData).getBytes(Charsets.UTF_8);
        checkState(data.length < MAX_DATA_SIZE, "Serialized form of ServiceEndPoint must be < 1MB.");

        String path = makeEndPointPath(endPoint);
        PersistentEphemeralNode oldNode = _nodes.put(path, _nodeFactory.create(path, data));
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
        PersistentEphemeralNode node = _nodes.remove(path);
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

        for (PersistentEphemeralNode node : _nodes.values()) {
            closeNode(node);
        }
        _nodes.clear();
        _metrics.close();
    }

    private void closeNode(PersistentEphemeralNode node) {
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
        private final CuratorFramework _curator;

        NodeFactory(CuratorFramework curator) {
            _curator = checkNotNull(curator);
        }

        PersistentEphemeralNode create(String path, byte[] data) {
            return new PersistentEphemeralNode(_curator, path, data, CreateMode.EPHEMERAL);
        }
    }
}
