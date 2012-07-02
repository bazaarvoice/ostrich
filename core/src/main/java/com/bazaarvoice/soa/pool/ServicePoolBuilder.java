package com.bazaarvoice.soa.pool;

import com.bazaarvoice.soa.HostDiscovery;
import com.bazaarvoice.soa.HostDiscoverySource;
import com.bazaarvoice.soa.RetryPolicy;
import com.bazaarvoice.soa.ServiceFactory;
import com.bazaarvoice.soa.discovery.ZooKeeperHostDiscovery;
import com.bazaarvoice.soa.zookeeper.ZooKeeperConnection;
import com.google.common.base.Ticker;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.ThreadFactoryBuilder;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.lang.String.format;

public class ServicePoolBuilder<S> {
    private final Class<S> _serviceType;
    private final List<HostDiscoverySource> _hostDiscoverySources = Lists.newArrayList();
    private ServiceFactory<S> _serviceFactory;
    private ScheduledExecutorService _healthCheckExecutor;

    public static <S> ServicePoolBuilder<S> create(Class<S> serviceType) {
        return new ServicePoolBuilder<S>(serviceType);
    }

    private ServicePoolBuilder(Class<S> serviceType) {
        _serviceType = checkNotNull(serviceType);
    }

    /**
     * Adds a {@link HostDiscoverySource} instance to the builder.  Multiple instances of {@code HostDiscoverySource}
     * may be specified.  The service pool will use the first source to return a non-null instance of
     * {@link HostDiscovery} for the service name returned by {@link ServiceFactory#getServiceName()}.
     *
     * @param hostDiscoverySource a host discovery source to use to find the {@link HostDiscovery} when constructing
     * the {@link ServicePool}
     * @return this
     */
    public ServicePoolBuilder<S> withHostDiscoverySource(HostDiscoverySource hostDiscoverySource) {
        _hostDiscoverySources.add(checkNotNull(hostDiscoverySource));
        return this;
    }

    /**
     * Adds a {@link HostDiscovery} instance to the builder.  The service pool will use this {@code HostDiscovery}
     * instance unless a preceding call to {@link #withHostDiscoverySource(HostDiscoverySource)} provides a non-null
     * instance of {@code HostDiscovery}.
     * <p>
     * Once this method is called, any subsequent calls to host discovery-related methods on this builder instance are
     * ignored.
     *
     * @param hostDiscovery the host discovery instance to use in the built {@link ServicePool}
     * @return this
     */
    public ServicePoolBuilder<S> withHostDiscovery(final HostDiscovery hostDiscovery) {
        checkNotNull(hostDiscovery);
        return withHostDiscoverySource(new HostDiscoverySource() {
            @Override
            public HostDiscovery forService(String serviceName) {
                return hostDiscovery;
            }
        });
    }

    /**
     * Adds a {@link ZooKeeperConnection} instance to the builder that will be used for host discovery.  The service
     * pool will use ZooKeeper for host discovery unless a preceding call to
     * {@link #withHostDiscoverySource(HostDiscoverySource)} provides a non-null instance of {@code HostDiscovery}.
     * <p>
     * Once this method is called, any subsequent calls to host discovery-related methods on this builder instance are
     * ignored.
     *
     * @param connection the ZooKeeper connection to use for host discovery
     * @return this
     */
    public ServicePoolBuilder<S> withZooKeeperHostDiscovery(final ZooKeeperConnection connection) {
        checkNotNull(connection);
        return withHostDiscoverySource(new HostDiscoverySource() {
            @Override
            public HostDiscovery forService(String serviceName) {
                return new ZooKeeperHostDiscovery(connection, serviceName);
            }
        });
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
        return buildInternal();
    }

    private ServicePool<S> buildInternal() {
        checkNotNull(_serviceFactory);

        String serviceName = _serviceFactory.getServiceName();
        HostDiscovery hostDiscovery = findHostDiscovery(serviceName);

        boolean shutdownOnClose = (_healthCheckExecutor == null);
        if (_healthCheckExecutor == null) {
            ThreadFactory daemonThreadFactory = new ThreadFactoryBuilder()
                    .setNameFormat(serviceName + "-HealthChecks-%d")
                    .setDaemon(true)
                    .build();
            _healthCheckExecutor = Executors.newScheduledThreadPool(1, daemonThreadFactory);
        }

        return new ServicePool<S>(_serviceType, Ticker.systemTicker(), hostDiscovery, _serviceFactory,
                _healthCheckExecutor, shutdownOnClose);
    }

    private HostDiscovery findHostDiscovery(String serviceName) {
        for (HostDiscoverySource source : _hostDiscoverySources) {
            HostDiscovery hostDiscovery = source.forService(serviceName);
            if (hostDiscovery != null) {
                return hostDiscovery;
            }
        }
        throw new IllegalStateException(format("No HostDiscovery found for service: %s", serviceName));
    }

    /**
     * Builds a dynamic proxy that wraps a {@code ServicePool} and implements the service interface directly.  This is
     * appropriate for stateless services where it's sensible for the same retry policy to apply to every method.
     * <p>
     * It is the caller's responsibility to shutdown the service pool when they're done with it by casting the proxy
     * to {@link java.io.Closeable} and calling the {@link java.io.Closeable#close()} method.
     * @param retryPolicy The retry policy to apply for every service call.
     * @return The dynamic proxy instance that implements the service interface {@code S} and the
     * {@link java.io.Closeable} interface.
     */
    public S buildProxy(RetryPolicy retryPolicy) {
        return buildInternal().newProxy(retryPolicy, true);
    }
}