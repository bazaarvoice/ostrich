package com.bazaarvoice.soa.discovery;

import com.bazaarvoice.soa.HostDiscovery;
import com.bazaarvoice.soa.ServiceEndPoint;
import com.bazaarvoice.soa.ServiceEndPointJsonCodec;
import com.bazaarvoice.soa.metrics.Metrics;
import com.bazaarvoice.soa.registry.ZooKeeperServiceRegistry;
import com.bazaarvoice.zookeeper.ZooKeeperConnection;
import com.bazaarvoice.zookeeper.recipes.discovery.NodeDataParser;
import com.bazaarvoice.zookeeper.recipes.discovery.NodeListener;
import com.bazaarvoice.zookeeper.recipes.discovery.ZooKeeperNodeDiscovery;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Charsets;
import com.google.common.collect.ConcurrentHashMultiset;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.collect.Multiset;
import com.google.common.collect.Sets;
import com.yammer.metrics.core.Counter;
import com.yammer.metrics.core.Gauge;
import com.yammer.metrics.core.Meter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * The <code>ZooKeeperHostDiscovery</code> class encapsulates ZooKeeperNodeDiscovery which
 * watches a service path in ZooKeeper and will monitor which hosts are
 * available.  As hosts come and go the results of calling the <code>#getHosts</code> method changes.
 */
public class ZooKeeperHostDiscovery implements HostDiscovery {
    private static final Logger LOG = LoggerFactory.getLogger(ZooKeeperHostDiscovery.class);

    private final ZooKeeperNodeDiscovery<ServiceEndPoint> _nodeDiscovery;
    private final Multiset<ServiceEndPoint> _endPoints;
    private final Set<EndPointListener> _listeners;

    private final Metrics _metrics;
    private final Counter _numListeners;
    private final Meter _numZooKeeperAdds;
    private final Meter _numZooKeeperRemoves;
    private final Meter _numZooKeeperChanges;

    public ZooKeeperHostDiscovery(ZooKeeperConnection connection, String serviceName) {
        this(new NodeDiscoveryFactory(), connection, serviceName);
    }

    @VisibleForTesting
    ZooKeeperHostDiscovery(NodeDiscoveryFactory factory, ZooKeeperConnection connection, String serviceName) {
        checkNotNull(connection);
        checkNotNull(serviceName);
        checkArgument(!"".equals(serviceName));

        String servicePath = ZooKeeperServiceRegistry.makeServicePath(serviceName);

        _listeners = Sets.newSetFromMap(Maps.<EndPointListener, Boolean>newConcurrentMap());
        _endPoints = ConcurrentHashMultiset.create();

        _nodeDiscovery = factory.create(
                connection,
                servicePath,
                new NodeDataParser<ServiceEndPoint>() {
                    public ServiceEndPoint parse(String path, byte[] nodeData) {
                        String json = new String(nodeData, Charsets.UTF_8);
                        return ServiceEndPointJsonCodec.fromJson(json);
                    }
                }
        );

        _nodeDiscovery.addListener(new ServiceListener());

        _metrics = Metrics.forInstance(this, serviceName);
        _metrics.newGauge(serviceName, "num-end-points", new Gauge<Integer>() {
            @Override
            public Integer value() {
                return Iterables.size(getHosts());
            }
        });

        _numListeners = _metrics.newCounter(serviceName, "num-listeners");
        _numZooKeeperAdds = _metrics.newMeter(serviceName, "num-zookeeper-adds", "adds", TimeUnit.MINUTES);
        _numZooKeeperRemoves = _metrics.newMeter(serviceName, "num-zookeeper-removes", "removes", TimeUnit.MINUTES);
        _numZooKeeperChanges = _metrics.newMeter(serviceName, "num-zookeeper-changes", "changes", TimeUnit.MINUTES);

        // wait to start node discovery until all fields are initialized.
        _nodeDiscovery.start();
    }

    @Override
    public Iterable<ServiceEndPoint> getHosts() {
        return Iterables.unmodifiableIterable(_endPoints.elementSet());
    }

    @Override
    public boolean contains(ServiceEndPoint endPoint) {
        return _endPoints.contains(endPoint);
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

    private void addServiceEndPoint(ServiceEndPoint serviceEndPoint) {
        // add returns the number of instances that were in the Multiset before the add.
        if (_endPoints.add(serviceEndPoint, 1) == 0) {
            fireAddEvent(serviceEndPoint);
        }
    }

    private void removeServiceEndPoint(ServiceEndPoint serviceEndPoint) {
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
     * A zookeeper-common {@code NodeListener}
     */
    private final class ServiceListener implements NodeListener<ServiceEndPoint> {
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
        ZooKeeperNodeDiscovery<ServiceEndPoint> create(ZooKeeperConnection connection, String path,
                                                       NodeDataParser<ServiceEndPoint> parser) {
            return new ZooKeeperNodeDiscovery<ServiceEndPoint>(connection, path, parser);
        }
    }
}
