package com.bazaarvoice.ostrich.pool;

import com.bazaarvoice.ostrich.MultiThreadedServiceFactory;
import com.bazaarvoice.ostrich.ServiceFactory;
import com.codahale.metrics.MetricRegistry;
import com.google.common.util.concurrent.ThreadFactoryBuilder;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * The type Service cache builder.
 *
 * @param <S> the type parameter
 */
public class ServiceCacheBuilder<S> {

    private ServiceCachingPolicy _cachingPolicy;
    private ServiceFactory<S> _serviceFactory;
    private MetricRegistry _metricRegistry;

    /**
     * With caching policy service cache builder.
     *
     * @param cachingPolicy the caching policy
     * @return the service cache builder
     */
    public ServiceCacheBuilder<S> withCachingPolicy(ServiceCachingPolicy cachingPolicy) {
        _cachingPolicy = cachingPolicy;
        return this;
    }

    /**
     * With service factory service cache builder.
     *
     * @param serviceFactory the service factory
     * @return the service cache builder
     */
    public ServiceCacheBuilder<S> withServiceFactory(ServiceFactory<S> serviceFactory) {
        _serviceFactory = serviceFactory;
        return this;
    }

    /**
     * With metric registry service cache builder.
     *
     * @param metricRegistry the metric registry
     * @return the service cache builder
     */
    public ServiceCacheBuilder<S> withMetricRegistry(MetricRegistry metricRegistry) {
        _metricRegistry = metricRegistry;
        return this;
    }

    /**
     * Build service cache.
     *
     * @return the service cache
     */
    public ServiceCache<S> build() {
        checkNotNull(_cachingPolicy, "cachingPolicy");
        if (_cachingPolicy.useMultiThreadedClientPolicy()) {
            checkNotNull(_serviceFactory, "serviceFactory");
            checkArgument((_serviceFactory instanceof MultiThreadedServiceFactory), "Please implement MultiThreadedServiceFactory to construct MultiThreadedClientServiceCache");
            return new MultiThreadedClientServiceCache<>((MultiThreadedServiceFactory<S>) _serviceFactory, _metricRegistry);
        }
        else {
            checkNotNull(_serviceFactory, "serviceFactory");
            checkNotNull(_metricRegistry, "metricRegistry");
            return new SingleThreadedClientServiceCache<>(_cachingPolicy, _serviceFactory, _metricRegistry);
        }
    }

    /**
     * This ensures the {@link java.util.concurrent.ScheduledExecutorService} in not loaded onto jvm
     * until the class is loaded by explicitly calling the constructor.
     *
     * @return the scheduled executor service
     */
    public static ScheduledExecutorService buildDefaultExecutor() {
        return Executors.newScheduledThreadPool(1,
                new ThreadFactoryBuilder().setNameFormat("ServiceCache-CleanupThread-%d").setDaemon(true).build());
    }
}
