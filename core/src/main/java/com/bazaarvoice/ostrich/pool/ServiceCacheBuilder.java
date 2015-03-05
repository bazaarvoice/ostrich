package com.bazaarvoice.ostrich.pool;

import com.bazaarvoice.ostrich.ServiceFactory;
import com.bazaarvoice.ostrich.ThreadSafeServiceFactory;
import com.codahale.metrics.MetricRegistry;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public class ServiceCacheBuilder<S> {

    private ServiceCachingPolicy _cachingPolicy;
    private ServiceFactory<S> _serviceFactory;
    private MetricRegistry _metricRegistry;

    public ServiceCacheBuilder<S> withCachingPolicy(ServiceCachingPolicy cachingPolicy) {
        _cachingPolicy = cachingPolicy;
        return this;
    }

    public ServiceCacheBuilder<S> withServiceFactory(ServiceFactory<S> serviceFactory) {
        _serviceFactory = serviceFactory;
        return this;
    }

    public ServiceCacheBuilder<S> withMetricRegistry(MetricRegistry metricRegistry) {
        _metricRegistry = metricRegistry;
        return this;
    }

    public ServiceCache<S> build() {
        checkNotNull(_cachingPolicy);
        if (_cachingPolicy.useMultiThreadedClientPolicy()) {
            checkArgument(_cachingPolicy.evictionTTLForMultiThreadedClientPolicy() >= 0);
            checkNotNull(_serviceFactory);
            if(!(_serviceFactory instanceof ThreadSafeServiceFactory)) {
                throw new IllegalArgumentException("Please implement ThreadSafeServiceFactory to construct MultiThreadedClientServiceCache");
            }
            return new MultiThreadedClientServiceCache<>((ThreadSafeServiceFactory<S>) _serviceFactory,
                    _cachingPolicy.evictionTTLForMultiThreadedClientPolicy(), _metricRegistry);
        }
        else {
            checkNotNull(_serviceFactory);
            checkNotNull(_metricRegistry);
            return new SingleThreadedClientServiceCache<>(_cachingPolicy, _serviceFactory, _metricRegistry);
        }
    }
}
