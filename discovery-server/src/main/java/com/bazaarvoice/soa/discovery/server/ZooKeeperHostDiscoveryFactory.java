package com.bazaarvoice.soa.discovery.server;

import com.bazaarvoice.soa.HostDiscovery;
import com.bazaarvoice.soa.discovery.ZooKeeperHostDiscovery;
import com.bazaarvoice.zookeeper.ZooKeeperConnection;
import com.google.common.base.Throwables;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.cache.RemovalListener;
import com.google.common.cache.RemovalNotification;
import com.google.inject.Inject;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Preconditions.checkNotNull;

public class ZooKeeperHostDiscoveryFactory {
    private final ZooKeeperConnection _zookeeper;

    /**
     * Loading cache containing all of the ZooKeeperHostDiscovery instances in the system.  This cache makes sure that
     * idle, unused discovery instances will be automatically expired from the cache and cleaned up.
     */
    private final LoadingCache<String, ZooKeeperHostDiscovery> _discoveryInstances = CacheBuilder.newBuilder()
            .expireAfterAccess(10, TimeUnit.MINUTES)
            .removalListener(new RemovalListener<String, ZooKeeperHostDiscovery>() {
                @Override
                public void onRemoval(RemovalNotification<String, ZooKeeperHostDiscovery> notification) {
                    // Make sure we close any host discovery object when it gets evicted from the cache
                    try {
                        ZooKeeperHostDiscovery discovery = notification.getValue();
                        if (discovery != null) {
                            discovery.close();
                        }
                    } catch (IOException e) {
                        throw Throwables.propagate(e);
                    }
                }
            })
            .build(new CacheLoader<String, ZooKeeperHostDiscovery>() {
                @Override
                public ZooKeeperHostDiscovery load(String serviceName) throws Exception {
                    return new ZooKeeperHostDiscovery(_zookeeper, serviceName);
                }
            });

    @Inject
    ZooKeeperHostDiscoveryFactory(ZooKeeperConnection zookeeper) {
        _zookeeper = checkNotNull(zookeeper);
    }

    /**
     * Retrieve the specific host discovery instance for the provided service.
     * <p/>
     * It is expected that the caller won't cache the returned discovery instance (the factory already does that for
     * them).
     */
    public HostDiscovery getHostDiscovery(String serviceName) {
        return _discoveryInstances.getUnchecked(serviceName);
    }
}
