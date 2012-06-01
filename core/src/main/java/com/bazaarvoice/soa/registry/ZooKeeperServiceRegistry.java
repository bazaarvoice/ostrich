package com.bazaarvoice.soa.registry;

import com.bazaarvoice.soa.ServiceEndpoint;
import com.bazaarvoice.soa.ServiceRegistry;
import com.bazaarvoice.soa.internal.CuratorConnection;
import com.bazaarvoice.soa.zookeeper.ZooKeeperConnection;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Charsets;
import com.google.common.base.Throwables;
import com.google.common.collect.Maps;
import com.netflix.curator.framework.CuratorFramework;
import com.netflix.curator.framework.state.ConnectionState;
import com.netflix.curator.framework.state.ConnectionStateListener;
import com.netflix.curator.utils.ZKPaths;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;

import java.util.Map;

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

    /** Number of attempts for the register and unregister operations to try to succeed. */
    private static final int NUM_ATTEMPTS = 3;

    private final CuratorFramework _curator;

    /** The ephemeral data that's been written to ZooKeeper.  Saved in case the connection is lost and then regained. */
    private final Map<String, byte[]> _ephemeralData = Maps.newConcurrentMap();

    public ZooKeeperServiceRegistry(ZooKeeperConnection connection) {
        this(((CuratorConnection) checkNotNull(connection)).getCurator());
    }

    @VisibleForTesting
    ZooKeeperServiceRegistry(CuratorFramework curator) {
        checkNotNull(curator);
        checkArgument(curator.isStarted());

        _curator = curator;

        // Setup a connection listener that re-establishes ephemeral node data when we reconnect to ZooKeeper.  This
        // ensures that as long as we have a healthy connection we'll do our best to make sure that our ephemeral node
        // data is written into ZooKeeper and that any registered services are available.
        _curator.getConnectionStateListenable().addListener(new ConnectionStateListener() {
            @Override
            public void stateChanged(CuratorFramework client, ConnectionState newState) {
                if (newState == ConnectionState.RECONNECTED) {
                    for (Map.Entry<String, byte[]> entry : _ephemeralData.entrySet()) {
                        String path = entry.getKey();
                        byte[] data = entry.getValue();
                        register(path, data);
                    }
                }
            }
        });
    }

    /** {@inheritDoc} */
    @Override
    public boolean register(ServiceEndpoint endpoint) {
        checkNotNull(endpoint);

        String path = makeEndpointPath(endpoint);
        byte[] data = endpoint.toJson().getBytes(Charsets.UTF_8);
        checkState(data.length < MAX_DATA_SIZE, "Serialized form of ServiceEndpoint must be < 1MB.");

        // Record the fact that we're interested in publishing this path as ephemeral node data...
        _ephemeralData.put(path, data);

        return register(path, data);
    }

    private boolean register(String path, byte[] data) {
        for (int i = 0; i < NUM_ATTEMPTS; i++) {
            try {
                _curator.create()
                        .creatingParentsIfNeeded()
                        .withMode(CreateMode.EPHEMERAL)
                        .forPath(path, data);
                return true;
            } catch (KeeperException.NodeExistsException e) {
                // Sometimes a server can restart faster than ZooKeeper can notice and clean up the ephemeral node.  So
                // when this happens we won't be able to create a new ephemeral node because one is already there.  This
                // is problematic because the existing ephemeral node isn't tied to our session with ZooKeeper and thus
                // not tied to our lifetime.  So in order to make sure that we end up creating a node tied to our
                // lifetime we will delete the existing node and create a new one from our session.
                if (!deleteNode(path)) {
                    // We weren't able to delete the node after trying multiple times.  Propagate the original
                    // exception to our caller as a RuntimeException.
                    throw Throwables.propagate(e);
                }
            } catch (Exception e) {
                // ignored
            }
        }

        return false;
    }

    /** {@inheritDoc} */
    @Override
    public boolean unregister(ServiceEndpoint endpoint) {
        checkNotNull(endpoint);

        String path = makeEndpointPath(endpoint);

        // Remove the ephemeral data that we're tracking...
        _ephemeralData.remove(path);

        for (int i = 0; i < NUM_ATTEMPTS; i++) {
            try {
                _curator.delete().forPath(path);
                return true;
            } catch (KeeperException.NoNodeException e) {
                // The node isn't there.  In this case, from our perspective, the service has been unregistered.  Just
                // return success.
                return true;
            } catch (Exception e) {
                // ignored
            }
        }

        return false;
    }

    /** Return the curator instance used by this registry. */
    @VisibleForTesting
    CuratorFramework getCurator() {
        return _curator;
    }

    private boolean deleteNode(String path) {
        for (int i = 0; i < NUM_ATTEMPTS; i++) {
            try {
                _curator.delete().forPath(path);
                return true;
            } catch (Exception e) {
                // ignored
            }
        }

        return false;
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
    @VisibleForTesting
    static String makeEndpointPath(ServiceEndpoint endpoint) {
        checkNotNull(endpoint);

        String servicePath = makeServicePath(endpoint.getServiceName());
        String serviceAddress = endpoint.getServiceAddress();
        return ZKPaths.makePath(servicePath, serviceAddress);
    }
}
