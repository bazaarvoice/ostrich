package com.bazaarvoice.soa.pool;

import com.bazaarvoice.soa.HostDiscovery;
import com.bazaarvoice.soa.HostDiscoverySource;
import com.bazaarvoice.soa.RetryPolicy;
import com.bazaarvoice.soa.ServiceFactory;
import com.bazaarvoice.soa.discovery.ZooKeeperHostDiscovery;
import com.bazaarvoice.zookeeper.ZooKeeperConnection;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.common.base.Ticker;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.ThreadFactoryBuilder;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.lang.String.format;

public class ServicePoolBuilder<S> {
    private static final int DEFAULT_NUM_HEALTH_CHECK_THREADS = 1;

    private final Class<S> _serviceType;
    private final List<HostDiscoverySource> _hostDiscoverySources = Lists.newArrayList();
    private String _ensembleName;
    private ServiceFactory<S> _serviceFactory;
    private ScheduledExecutorService _healthCheckExecutor;
    private ServiceCachingPolicy _cachingPolicy;
    private ExecutorService _asyncExecutor;

    public static <S> ServicePoolBuilder<S> create(Class<S> serviceType) {
        return new ServicePoolBuilder<S>(serviceType);
    }

    private ServicePoolBuilder(Class<S> serviceType) {
        _serviceType = checkNotNull(serviceType);
    }

    /**
     * Sets the name of the group of servers that collectively provide the service.  The ensemble name is optional,
     * but it can be used to distinguish multiple deployments of the same kind of service but for different purposes.
     *
     * @param ensembleName the name of the group of servers that collectively provide the service
     * @return this
     */
    public ServicePoolBuilder<S> withEnsembleName(String ensembleName) {
        _ensembleName = Strings.emptyToNull(ensembleName);
        return this;
    }

    /**
     * Adds a {@link HostDiscoverySource} instance to the builder.  Multiple instances of {@code HostDiscoverySource}
     * may be specified.  The service pool will use the first source to return a non-null instance of
     * {@link HostDiscovery} for the ensemble name and service type returned by {@link ServiceFactory#getServiceType()}.
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
            public HostDiscovery forService(String ensembleName, String serviceType) {
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
            public HostDiscovery forService(String ensembleName, String serviceType) {
                return new ZooKeeperHostDiscovery(connection, ensembleName, serviceType);
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
     * Adds an {@code ExecutorService} instance to the builder for use in executing asynchronous requests. The executor
     * is not used unless an asynchronous pool is built with the {@link #buildAsync} method.
     * <p/>
     * Adding an executor is optional.  If one isn't specified then one will be created and used automatically when
     * {@code buildAsync} is called.
     *
     * @param executor The {@code ExecutorService} to use
     * @return this
     */
    public ServicePoolBuilder<S> withAsyncExecutor(ExecutorService executor) {
        _asyncExecutor = checkNotNull(executor);
        return this;
    }

    /**
     * Enables caching of service instances in the built {@link ServicePool}.
     * <p/>
     * Specifying a caching policy is optional.  If one isn't specified then a default one that doesn't cache service
     * instances will be created and used automatically.
     *
     * @param policy The {@link ServiceCachingPolicy} to use
     * @return this
     */
    public ServicePoolBuilder<S> withCachingPolicy(ServiceCachingPolicy policy) {
        _cachingPolicy = checkNotNull(policy);
        return this;
    }

    /**
     * Builds a {@code com.bazaarvoice.soa.ServicePool}.
     *
     * @return The {@code com.bazaarvoice.soa.ServicePool} that was constructed.
     */
    public com.bazaarvoice.soa.ServicePool<S> build() {
        return buildInternal();
    }

    /**
     * Builds a {@code com.bazaarvoice.soa.AsyncServicePool}.
     *
     * @return The {@code com.bazaarvoice.soa.AsyncServicePool} that was constructed.
     */
    public com.bazaarvoice.soa.AsyncServicePool<S> buildAsync() {
        ServicePool<S> pool = buildInternal();

        boolean shutdownAsyncExecutorOnClose = (_asyncExecutor == null);
        if (_asyncExecutor == null) {
            ThreadFactory threadFactory = new ThreadFactoryBuilder()
                    .setNameFormat(getServiceName() + "-AsyncExecutorThread-%d")
                    .setDaemon(true)
                    .build();
            _asyncExecutor = Executors.newCachedThreadPool(threadFactory);
        }

        return new AsyncServicePool<S>(Ticker.systemTicker(), pool, true, _asyncExecutor, shutdownAsyncExecutorOnClose);
    }

    /**
     * Builds a dynamic proxy that wraps a {@code ServicePool} and implements the service interface directly.  This is
     * appropriate for stateless services where it's sensible for the same retry policy to apply to every method.
     * <p/>
     * It is the caller's responsibility to shutdown the service pool when they're done with it by casting the proxy
     * to {@link java.io.Closeable} and calling the {@link java.io.Closeable#close()} method.
     *
     * @param retryPolicy The retry policy to apply for every service call.
     * @return The dynamic proxy instance that implements the service interface {@code S} and the
     *         {@link java.io.Closeable} interface.
     */
    public S buildProxy(RetryPolicy retryPolicy) {
        return ServicePoolProxy.create(_serviceType, retryPolicy, build(), true);
    }

    @VisibleForTesting
    ServicePool<S> buildInternal() {
        checkNotNull(_serviceFactory);

        HostDiscovery hostDiscovery = findHostDiscovery(_serviceFactory.getServiceType(), _ensembleName);

        if (_cachingPolicy == null) {
            _cachingPolicy = ServiceCachingPolicyBuilder.NO_CACHING;
        }

        boolean shutdownHealthCheckExecutorOnClose = (_healthCheckExecutor == null);
        if (_healthCheckExecutor == null) {
            ThreadFactory threadFactory = new ThreadFactoryBuilder()
                    .setNameFormat(getServiceName() + "-HealthCheckThread-%d")
                    .setDaemon(true)
                    .build();
            _healthCheckExecutor = Executors.newScheduledThreadPool(DEFAULT_NUM_HEALTH_CHECK_THREADS, threadFactory);
        }

        return new ServicePool<S>(Ticker.systemTicker(), hostDiscovery, _serviceFactory,
                _cachingPolicy, _healthCheckExecutor, shutdownHealthCheckExecutorOnClose);
    }

    private HostDiscovery findHostDiscovery(String serviceType, String ensembleName) {
        for (HostDiscoverySource source : _hostDiscoverySources) {
            HostDiscovery hostDiscovery = source.forService(ensembleName, serviceType);
            if (hostDiscovery != null) {
                return hostDiscovery;
            }
        }
        throw new IllegalStateException(format("No HostDiscovery found for service: %s %s", ensembleName, serviceType));
    }

    private String getServiceName() {
        String serviceType = _serviceFactory.getServiceType();
        return (_ensembleName != null) ? _ensembleName + "-" + serviceType : serviceType;
    }
}