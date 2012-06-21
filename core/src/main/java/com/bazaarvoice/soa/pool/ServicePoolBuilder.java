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
import static com.google.common.base.Preconditions.checkState;

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
     * Adds a {@code HostDiscovery} instance to the builder.
     * <p/>
     * The last call to this method or {@link #withZooKeeperHostDiscovery} wins.
     *
     * @param hostDiscovery the host discovery instance to use in the built {@link ServicePool}
     * @return this
     */
    public ServicePoolBuilder<S> withHostDiscovery(HostDiscovery hostDiscovery) {
        _hostDiscovery = checkNotNull(hostDiscovery);
        return this;
    }

    /**
     * Adds a {@code ZooKeeperConnection} instance to the builder.
     * <p/>
     * Will be used in creation of a {@link ZooKeeperHostDiscovery} instance for the built {@link ServicePool}.
     * The last call to this method or {@link #withHostDiscovery} wins.
     *
     * @param connection the ZooKeeper connection to use for host discovery
     * @return this
     */
    public ServicePoolBuilder<S> withZooKeeperHostDiscovery(ZooKeeperConnection connection) {
        _zooKeeperConnection = checkNotNull(connection);
        _hostDiscovery = null;
        return this;
    }

    /**
     * Adds a {@code ServiceFactory} instance to the builder.
     *
     * @param serviceFactory the ServiceFactory to use
     * @return this
     */
    public ServicePoolBuilder<S> withServiceFactory(ServiceFactory<S> serviceFactory) {
        _serviceFactory = checkNotNull(serviceFactory);
        return this;
    }

    /**
     * Adds a {@code ScheduledExecutorService} instance to the builder for use in executing health checks.
     * <p/>
     * Adding an executor is optional.  If one isn't specified then one will be created and used automatically.
     *
     * @param executor The {@code ScheduledExecutorService} to use
     * @return this
     */
    public ServicePoolBuilder<S> withHealthCheckExecutor(ScheduledExecutorService executor) {
        _healthCheckExecutor = checkNotNull(executor);
        return this;
    }

    /**
     * Builds the {@code ServicePool}.
     *
     * @return The {@code ServicePool} that was constructed.
     */
    public com.bazaarvoice.soa.ServicePool<S> build() {
        checkNotNull(_serviceFactory);
        checkState(_hostDiscovery != null || _zooKeeperConnection != null);

        String serviceName = _serviceFactory.getServiceName();
        if (_hostDiscovery == null) {
            _hostDiscovery = new ZooKeeperHostDiscovery(_zooKeeperConnection, serviceName);
        }

        boolean shutdownOnClose = (_healthCheckExecutor == null);
        if (_healthCheckExecutor == null) {
            ThreadFactory daemonThreadFactory = new ThreadFactoryBuilder()
                    .setNameFormat(serviceName + "-HealthChecks-%d")
                    .setDaemon(true)
                    .build();
            _healthCheckExecutor = Executors.newScheduledThreadPool(1, daemonThreadFactory);
        }

        return new ServicePool<S>(_ticker, _hostDiscovery, _serviceFactory, _healthCheckExecutor, shutdownOnClose);
    }
}