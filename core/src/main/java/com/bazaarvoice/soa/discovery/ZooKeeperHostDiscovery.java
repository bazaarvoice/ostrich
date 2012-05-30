package com.bazaarvoice.soa.discovery;

import com.bazaarvoice.soa.HostDiscovery;
import com.bazaarvoice.soa.ServiceEndpoint;
import com.bazaarvoice.soa.internal.CuratorConfiguration;
import com.bazaarvoice.soa.registry.ZooKeeperServiceRegistry;
import com.bazaarvoice.soa.zookeeper.ZooKeeperConfiguration;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Charsets;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.netflix.curator.framework.CuratorFramework;
import com.netflix.curator.framework.recipes.cache.ChildData;
import com.netflix.curator.framework.recipes.cache.PathChildrenCache;
import com.netflix.curator.framework.recipes.cache.PathChildrenCacheEvent;
import com.netflix.curator.framework.recipes.cache.PathChildrenCacheListener;

import java.io.Closeable;
import java.io.IOException;
import java.util.Collection;
import java.util.Set;
import java.util.concurrent.ThreadFactory;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * The <code>ZooKeeperHostDiscovery</code> class watches a service path in ZooKeeper and will monitor which hosts are
 * available.  As hosts come and go the results of calling the <code>#getHosts</code> method changes.
 */
public class ZooKeeperHostDiscovery implements HostDiscovery, Closeable {
    private final CuratorFramework _curator;
    private final Set<ServiceEndpoint> _endpoints;
    private final Set<EndpointListener> _listeners;
    private final PathChildrenCache _pathCache;

    public ZooKeeperHostDiscovery(ZooKeeperConfiguration config, String serviceName) {
        this(((CuratorConfiguration) checkNotNull(config)).getCurator(), serviceName, true);
    }

    @VisibleForTesting
    ZooKeeperHostDiscovery(CuratorFramework curator, String serviceName, boolean waitForData) {
        checkNotNull(curator);
        checkNotNull(serviceName);
        checkArgument(curator.isStarted());
        checkArgument(!"".equals(serviceName));

        ThreadFactory threadFactory = new ThreadFactoryBuilder()
                .setNameFormat(getClass().getSimpleName() + "(" + serviceName + ")-%d")
                .setDaemon(true)
                .build();
        String servicePath = ZooKeeperServiceRegistry.makeServicePath(serviceName);

        _curator = curator;
        _endpoints = Sets.newSetFromMap(Maps.<ServiceEndpoint, Boolean>newConcurrentMap());
        _listeners = Sets.newSetFromMap(Maps.<EndpointListener, Boolean>newConcurrentMap());

        _pathCache = new PathChildrenCache(_curator, servicePath, true, threadFactory);
        _pathCache.getListenable().addListener(new ServiceListener());

        try {
            _pathCache.start(waitForData);
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }

        // No need to fire events because there aren't any listeners yet.
        for (ChildData childData : _pathCache.getCurrentData()) {
            _endpoints.add(toEndpoint(childData));
        }
    }

    @Override
    public Iterable<ServiceEndpoint> getHosts() {
        return _endpoints;
    }

    @Override
    public void addListener(EndpointListener listener) {
        _listeners.add(listener);
    }

    @Override
    public void removeListener(EndpointListener listener) {
        _listeners.remove(listener);
    }

    @Override
    public void close() throws IOException {
        _pathCache.close();
        _endpoints.clear();
    }

    @VisibleForTesting
    CuratorFramework getCurator() {
        return _curator;
    }

    private void fireAddEvent(ServiceEndpoint endpoint) {
        for (EndpointListener listener : _listeners) {
            listener.onEndpointAdded(endpoint);
        }
    }

    private void fireRemoveEvent(ServiceEndpoint endpoint) {
        for (EndpointListener listener : _listeners) {
            listener.onEndpointRemoved(endpoint);
        }
    }

    private ServiceEndpoint toEndpoint(ChildData data) {
        String json = new String(data.getData(), Charsets.UTF_16);
        return ServiceEndpoint.fromJson(json);
    }

    /** A curator <code>PathChildrenCacheListener</code> */
    private final class ServiceListener implements PathChildrenCacheListener {
        @Override
        public void childEvent(CuratorFramework client, PathChildrenCacheEvent event) throws Exception {
            if (event.getType() == PathChildrenCacheEvent.Type.RESET) {
                Collection<ServiceEndpoint> endpoints = ImmutableList.copyOf(_endpoints);
                _endpoints.clear();
                for (ServiceEndpoint endpoint : endpoints) {
                    fireRemoveEvent(endpoint);
                }
                return;
            }

            ServiceEndpoint endpoint = toEndpoint(event.getData());

            switch (event.getType()) {
                case CHILD_ADDED:
                    if (_endpoints.add(endpoint)) {
                        fireAddEvent(endpoint);
                    }
                    break;

                case CHILD_REMOVED:
                    if (_endpoints.remove(endpoint)) {
                        fireRemoveEvent(endpoint);
                    }
                    break;

                case CHILD_UPDATED:
                    // TODO: This should never happen.  Assert?  Log?
                    break;
            }
        }
    }
}
