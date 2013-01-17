package com.bazaarvoice.soa.discovery;

import com.bazaarvoice.soa.ServiceDiscovery;
import com.bazaarvoice.soa.registry.ZooKeeperServiceRegistry;
import com.bazaarvoice.zookeeper.ZooKeeperConnection;
import com.bazaarvoice.zookeeper.recipes.discovery.NodeDataParser;
import com.bazaarvoice.zookeeper.recipes.discovery.ZooKeeperNodeDiscovery;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Iterables;

import java.io.IOException;
import java.util.Map;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * The <code>ZooKeeperServiceDiscovery</code> class encapsulates ZooKeeperNodeDiscovery which
 * watches a the root service path in ZooKeeper and will monitor which services are
 * known.  As services come and go the results of calling the <code>#getServices</code>
 * method changes.
 * <p/>
 * NOTE: It's possible that a returned service doesn't have any {@code ServiceEndPoint}
 * instances currently registered.
 */
public class ZooKeeperServiceDiscovery implements ServiceDiscovery {
    /** The path in ZooKeeper where ostrich services are registered.  Ignoring namespaces. */
    @VisibleForTesting
    static final String SERVICE_PATH = ZooKeeperServiceRegistry.rootServicePath();

    /** Node data parser that returns the service name of the path. */
    @VisibleForTesting
    static final NodeDataParser<String> SERVICE_NAME_PARSER = new NodeDataParser<String>() {
        @Override
        public String parse(String path, byte[] nodeData) {
            path = path.substring(SERVICE_PATH.length());
            if (path.startsWith("/")) {
                path = path.substring(1);
            }

            return path;
        }
    };

    private final ZooKeeperNodeDiscovery<String> _nodeDiscovery;

    public ZooKeeperServiceDiscovery(ZooKeeperConnection connection) {
        this(new ZooKeeperNodeDiscovery<String>(connection, SERVICE_PATH, SERVICE_NAME_PARSER));
    }

    @VisibleForTesting
    ZooKeeperServiceDiscovery(ZooKeeperNodeDiscovery<String> nodeDiscovery) {
        _nodeDiscovery = checkNotNull(nodeDiscovery);
        _nodeDiscovery.start();
    }

    @Override
    public Iterable<String> getServices() {
        Map<String, String> nodes = _nodeDiscovery.getNodes();
        return Iterables.unmodifiableIterable(nodes.values());
    }

    @Override
    public void close() throws IOException {
        _nodeDiscovery.close();
    }
}
