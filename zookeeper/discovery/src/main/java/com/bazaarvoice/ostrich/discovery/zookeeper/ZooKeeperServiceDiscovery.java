package com.bazaarvoice.ostrich.discovery.zookeeper;

import com.bazaarvoice.curator.recipes.NodeDiscovery;
import com.bazaarvoice.ostrich.ServiceDiscovery;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Iterables;
import com.netflix.curator.framework.CuratorFramework;

import java.io.IOException;
import java.util.Map;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * The <code>ServiceDiscovery</code> class encapsulates a ZooKeeper backed NodeDiscovery which watches the root service
 * path in ZooKeeper and will monitor which services are known to exist.  As services come and go the results of calling
 * the <code>#getServices</code> method will change.
 * <p/>
 * NOTE: It's possible that a returned service doesn't have any {@code ServiceEndPoint} instances currently registered.
 * This class only watches the root service path's child nodes -- it doesn't make sure that a service has child nodes
 * of its own.
 */
public class ZooKeeperServiceDiscovery implements ServiceDiscovery {
    /**
     * The root path in ZooKeeper for where service registrations are stored.
     * <p/>
     * WARNING: Do not modify this without also modifying the ALL of the corresponding paths in the service registry,
     * host discovery, and service discovery classes!!!
     */
    @VisibleForTesting
    static final String ROOT_SERVICES_PATH = "/ostrich";

    /** Node data parser that returns the service name of the path. */
    @VisibleForTesting
    static final NodeDiscovery.NodeDataParser<String> SERVICE_NAME_PARSER = new NodeDiscovery.NodeDataParser<String>() {
        @Override
        public String parse(String path, byte[] nodeData) {
            path = path.substring(ROOT_SERVICES_PATH.length());
            if (path.startsWith("/")) {
                path = path.substring(1);
            }

            return path;
        }
    };

    private final NodeDiscovery<String> _nodeDiscovery;

    public ZooKeeperServiceDiscovery(CuratorFramework curator) {
        this(new NodeDiscovery<String>(curator, ROOT_SERVICES_PATH, SERVICE_NAME_PARSER));
    }

    @VisibleForTesting
    ZooKeeperServiceDiscovery(NodeDiscovery<String> nodeDiscovery) {
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
