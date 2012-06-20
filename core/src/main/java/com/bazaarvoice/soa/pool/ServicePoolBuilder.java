package com.bazaarvoice.soa.pool;

import com.bazaarvoice.soa.HostDiscovery;
import com.bazaarvoice.soa.ServiceFactory;
import com.bazaarvoice.soa.discovery.ZooKeeperHostDiscovery;
import com.bazaarvoice.soa.zookeeper.ZooKeeperConnection;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Ticker;
import com.google.common.util.concurrent.ThreadFactoryBuilder;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;

import static com.google.common.base.Preconditions.checkNotNull;

public class ServicePoolBuilder<S> {
    private Ticker _ticker = Ticker.systemTicker();
    private HostDiscovery _hostDiscovery;
    private ServiceFactory<S> _serviceFactory;
    private ScheduledExecutorService _healthCheckExecutor;
    private ZooKeeperConnection _zooKeeperConnection;

    @VisibleForTesting
    ServicePoolBuilder<S> withTicker(Ticker ticker) {
        _ticker = checkNotNull(ticker);
        return this;
    }

    /**
     * Adds a {@code HostDiscovery} instance to the builder. The last call to this method or {@link #withZooKeeperHostDiscovery} wins.
     * @param hostDiscovery the host discovery instance to use in the built {@link ServicePool}
     * @return this
     */
    public ServicePoolBuilder<S> withHostDiscovery(HostDiscovery hostDiscovery) {
        _hostDiscovery = checkNotNull(hostDiscovery);
        return this;
    }

    /**
     * Adds a {@code ZooKeeperConnection} instance to the builder.
     * Will be used in creation of a {@link ZooKeeperHostDiscovery} instance for the built {@link ServicePool}.
     * The last call to this method or {@link #withHostDiscovery} wins.
     * @param connection the ZooKeeper connection to use for host discovery
     * @return this
     */
    public ServicePoolBuilder<S> withZooKeeperHostDiscovery(ZooKeeperConnection connection) {
        _zooKeeperConnection = checkNotNull(connection);
        _hostDiscovery = null;
        return this;
    }

    public ServicePoolBuilder<S> withServiceFactory(ServiceFactory<S> serviceFactory) {
        _serviceFactory = checkNotNull(serviceFactory);
        return this;
    }

    public ServicePoolBuilder<S> withHealthCheckExecutor(ScheduledExecutorService executor) {
        _healthCheckExecutor = checkNotNull(executor);
        return this;
    }

    public com.bazaarvoice.soa.ServicePool<S> build() {
        checkNotNull(_serviceFactory);
        final String serviceName = _serviceFactory.getServiceName();
        if (_hostDiscovery == null && _zooKeeperConnection != null) {
            _hostDiscovery = new ZooKeeperHostDiscovery(_zooKeeperConnection, serviceName);
        }
        if (_healthCheckExecutor == null) {
            ThreadFactory daemonThreadFactory = new ThreadFactoryBuilder().setNameFormat(serviceName + "-HealthChecks-%d").setDaemon(true).build();
            _healthCheckExecutor = Executors.newScheduledThreadPool(1, daemonThreadFactory);
        }
        checkNotNull(_hostDiscovery);
        checkNotNull(_healthCheckExecutor);

        return new ServicePool<S>(_ticker, _hostDiscovery, _serviceFactory, _healthCheckExecutor);
    }
}