package com.bazaarvoice.ostrich.discovery.zookeeper;

import com.bazaarvoice.curator.recipes.NodeDiscovery;
import com.bazaarvoice.ostrich.HostDiscovery;
import com.bazaarvoice.ostrich.ServiceEndPoint;
import com.bazaarvoice.ostrich.ServiceEndPointJsonCodec;
import com.bazaarvoice.ostrich.metrics.Metrics;
import com.codahale.metrics.Counter;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Charsets;
import com.google.common.collect.ConcurrentHashMultiset;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.collect.Multiset;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.utils.ZKPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collections;
import java.util.Set;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * The <code>HostDiscovery</code> class encapsulates a ZooKeeper backed NodeDiscovery which watches a specific service
 * path in ZooKeeper and will monitor which end points are known to exist.  As end pionts come and go the results of
 * calling the {@link #getHosts} method change.
 */
public class ZooKeeperHostDiscovery implements HostDiscovery {
    private static final Logger LOG = LoggerFactory.getLogger(ZooKeeperHostDiscovery.class);

    /**
     * The root path in ZooKeeper for where service registrations are stored.
     *
     * WARNING: Do not modify this without also modifying the ALL of the corresponding paths in the service registry,
     * host discovery, and service discovery classes!!!
     */
    @VisibleForTesting
    static final String ROOT_SERVICES_PATH = "/ostrich";

    private final NodeDiscovery<ServiceEndPoint> _nodeDiscovery;
    private final Multiset<ServiceEndPoint> _endPoints;
    private final Set<EndPointListener> _listeners;

    private final Metrics.InstanceMetrics _metrics;
    private final Counter _numListeners;
    private final Meter _numZooKeeperAdds;
    private final Meter _numZooKeeperRemoves;
    private final Meter _numZooKeeperChanges;

    public ZooKeeperHostDiscovery(CuratorFramework curator, String serviceName, MetricRegistry metrics) {
        this(new NodeDiscoveryFactory(), curator, serviceName, metrics);
    }

    @VisibleForTesting
    ZooKeeperHostDiscovery(NodeDiscoveryFactory factory, CuratorFramework curator, String serviceName,
                           MetricRegistry metrics) {
        checkNotNull(factory);
        checkNotNull(curator);
        checkNotNull(serviceName);
        checkArgument(!"".equals(serviceName));
        checkNotNull(metrics);

        String servicePath = makeServicePath(serviceName);

        _listeners = Collections.newSetFromMap(Maps.<EndPointListener, Boolean>newConcurrentMap());
        _endPoints = ConcurrentHashMultiset.create();

        _nodeDiscovery = factory.create(
                curator,
                servicePath,
                new NodeDiscovery.NodeDataParser<ServiceEndPoint>() {
                    public ServiceEndPoint parse(String path, byte[] nodeData) {
                        String json = new String(nodeData, Charsets.UTF_8);
                        return ServiceEndPointJsonCodec.fromJson(json);
                    }
                }
        );

        _nodeDiscovery.addListener(new ServiceListener());

        _metrics = Metrics.forInstance(metrics, this, serviceName);
        _metrics.gauge("num-end-points", new Gauge<Integer>() {
            @Override
            public Integer getValue() {
                return Iterables.size(getHosts());
            }
        });

        _numListeners = _metrics.counter("num-listeners");
        _numZooKeeperAdds = _metrics.meter("num-zookeeper-adds");
        _numZooKeeperRemoves = _metrics.meter("num-zookeeper-removes");
        _numZooKeeperChanges = _metrics.meter("num-zookeeper-changes");

        // wait to start node discovery until all fields are initialized.
        _nodeDiscovery.start();
    }

    @Override
    public Iterable<ServiceEndPoint> getHosts() {
        return Iterables.unmodifiableIterable(_endPoints.elementSet());
    }

    @Override
    public void addListener(EndPointListener listener) {
        _listeners.add(listener);
        _numListeners.inc();
    }

    @Override
    public void removeListener(EndPointListener listener) {
        _listeners.remove(listener);
        _numListeners.dec();
    }

    @Override
    public void close() throws IOException {
        _nodeDiscovery.close();
        _endPoints.clear();
        _metrics.close();
    }

    @VisibleForTesting
    void addServiceEndPoint(ServiceEndPoint serviceEndPoint) {
        // add returns the number of instances that were in the Multiset before the add.
        if (_endPoints.add(serviceEndPoint, 1) == 0) {
            fireAddEvent(serviceEndPoint);
        }
    }

    @VisibleForTesting
    void removeServiceEndPoint(ServiceEndPoint serviceEndPoint) {
        // remove returns the number of instances that were in the Multiset before the remove.
        if (_endPoints.remove(serviceEndPoint, 1) == 1) {
            fireRemoveEvent(serviceEndPoint);
        }
    }

    private void fireAddEvent(ServiceEndPoint endPoint) {
        for (EndPointListener listener : _listeners) {
            listener.onEndPointAdded(endPoint);
        }
    }

    private void fireRemoveEvent(ServiceEndPoint endPoint) {
        for (EndPointListener listener : _listeners) {
            listener.onEndPointRemoved(endPoint);
        }
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
     * A zookeeper-common {@code NodeListener}
     */
    private final class ServiceListener implements NodeDiscovery.NodeListener<ServiceEndPoint> {
        @Override
        public void onNodeAdded(String path, ServiceEndPoint node) {
            _numZooKeeperAdds.mark();
            addServiceEndPoint(node);
        }

        @Override
        public void onNodeRemoved(String path, ServiceEndPoint node) {
            _numZooKeeperRemoves.mark();
            removeServiceEndPoint(node);
        }

        @Override
        public void onNodeUpdated(String path, ServiceEndPoint node) {
            _numZooKeeperChanges.mark();
            LOG.info("ServiceEndPoint data changed unexpectedly. End point ID: {}; ZooKeeperPath {}",
                    node.getId(), path);
        }
    }

    @VisibleForTesting
    static class NodeDiscoveryFactory {
        NodeDiscovery<ServiceEndPoint> create(CuratorFramework curator, String path,
                                              NodeDiscovery.NodeDataParser<ServiceEndPoint> parser) {
            return new NodeDiscovery<>(curator, path, parser);
        }
    }
}
