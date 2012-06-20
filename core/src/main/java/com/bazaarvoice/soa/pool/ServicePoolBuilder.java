package com.bazaarvoice.soa.pool;

import com.bazaarvoice.soa.HostDiscovery;
import com.bazaarvoice.soa.ServiceFactory;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Ticker;

import java.util.concurrent.ScheduledExecutorService;

import static com.google.common.base.Preconditions.checkNotNull;

public class ServicePoolBuilder<S> {
    private Ticker _ticker = Ticker.systemTicker();
    private HostDiscovery _hostDiscovery;
    private ServiceFactory<S> _serviceFactory;
    private ScheduledExecutorService _healthCheckExecutor;

    @VisibleForTesting
    ServicePoolBuilder<S> withTicker(Ticker ticker) {
        _ticker = checkNotNull(ticker);
        return this;
    }

    public ServicePoolBuilder<S> withHostDiscovery(HostDiscovery hostDiscovery) {
        _hostDiscovery = checkNotNull(hostDiscovery);
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
        checkNotNull(_hostDiscovery);
        checkNotNull(_serviceFactory);
        checkNotNull(_healthCheckExecutor);

        return new ServicePool<S>(_ticker, _hostDiscovery, _serviceFactory, _healthCheckExecutor);
    }
}