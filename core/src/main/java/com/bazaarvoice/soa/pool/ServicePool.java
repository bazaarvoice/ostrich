package com.bazaarvoice.soa.pool;

import com.bazaarvoice.soa.HostDiscovery;
import com.bazaarvoice.soa.LoadBalanceAlgorithm;
import com.bazaarvoice.soa.RetryPolicy;
import com.bazaarvoice.soa.ServiceCallback;
import com.bazaarvoice.soa.ServiceEndPoint;
import com.bazaarvoice.soa.ServiceFactory;
import com.bazaarvoice.soa.exceptions.MaxRetriesException;
import com.bazaarvoice.soa.exceptions.NoAvailableHostsException;
import com.bazaarvoice.soa.exceptions.NoSuitableHostsException;
import com.bazaarvoice.soa.exceptions.OnlyBadHostsException;
import com.bazaarvoice.soa.exceptions.ServiceException;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.base.Stopwatch;
import com.google.common.base.Throwables;
import com.google.common.base.Ticker;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import javax.annotation.Nullable;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Preconditions.checkNotNull;

class ServicePool<S> implements com.bazaarvoice.soa.ServicePool<S> {
    // By default check every minute to see if a previously unhealthy endpoint has become healthy.
    @VisibleForTesting
    static final long HEALTH_CHECK_POLL_INTERVAL_IN_SECONDS = 60;

    private final Ticker _ticker;
    private final HostDiscovery _hostDiscovery;
    private final HostDiscovery.EndpointListener _hostDiscoveryListener;
    private final ServiceFactory<S> _serviceFactory;
    private final ExceptionMapper _exceptionMapper;
    private final ScheduledExecutorService _healthCheckExecutor;
    private final boolean _shutdownHealthCheckExecutorOnClose;
    private final LoadBalanceAlgorithm _loadBalanceAlgorithm;
    private final Set<ServiceEndPoint> _badEndpoints;
    private final Predicate<ServiceEndPoint> _badEndpointFilter;
    private final Set<ServiceEndPoint> _recentlyRemovedEndpoints;
    private final Future<?> _batchHealthChecksFuture;

    ServicePool(Ticker ticker, HostDiscovery hostDiscovery, ServiceFactory<S> serviceFactory,
                @Nullable ExceptionMapper exceptionMapper, ScheduledExecutorService healthCheckExecutor,
                boolean shutdownExecutorOnClose) {
        _ticker = checkNotNull(ticker);
        _hostDiscovery = checkNotNull(hostDiscovery);
        _serviceFactory = checkNotNull(serviceFactory);
        _exceptionMapper = exceptionMapper;
        _healthCheckExecutor = checkNotNull(healthCheckExecutor);
        _shutdownHealthCheckExecutorOnClose = shutdownExecutorOnClose;
        _loadBalanceAlgorithm = checkNotNull(_serviceFactory.getLoadBalanceAlgorithm());
        _badEndpoints = Sets.newSetFromMap(Maps.<ServiceEndPoint, Boolean>newConcurrentMap());
        _badEndpointFilter = Predicates.not(Predicates.in(_badEndpoints));
        _recentlyRemovedEndpoints = Sets.newSetFromMap(CacheBuilder.newBuilder()
                .ticker(_ticker)
                .expireAfterWrite(10, TimeUnit.MINUTES)  // TODO: Make this a constant
                .<ServiceEndPoint, Boolean>build()
                .asMap());

        // Watch endpoints as they are removed from host discovery so that we can remove them from our set of bad
        // endpoints as well.  This will prevent the badEndpoints set from growing in an unbounded fashion.  There is a
        // minor race condition that could happen here, but it's not anything to be concerned about.  The HostDiscovery
        // component could lose its connection to its backing data store and then immediately regain it right
        // afterwards.  If that happens it could remove all of its endpoints only to re-add them right back again and we
        // will "forget" that an endpoint was bad and try to use it again.  This isn't fatal though because we'll just
        // rediscover that it's a bad endpoint again in the future.  Also in the future it might be useful to measure
        // how long an endpoint has been considered bad and potentially take action for endpoints that are bad for long
        // periods of time.
        _hostDiscoveryListener = new HostDiscovery.EndpointListener() {
            @Override
            public void onEndpointAdded(ServiceEndPoint endpoint) {
                addEndpoint(endpoint);
            }

            @Override
            public void onEndpointRemoved(ServiceEndPoint endpoint) {
                removeEndpoint(endpoint);
            }
        };
        _hostDiscovery.addListener(_hostDiscoveryListener);

        // Periodically wake up and check any badEndpoints to see if they're now healthy.
        _batchHealthChecksFuture = _healthCheckExecutor.scheduleAtFixedRate(new BatchHealthChecks(),
                HEALTH_CHECK_POLL_INTERVAL_IN_SECONDS, HEALTH_CHECK_POLL_INTERVAL_IN_SECONDS, TimeUnit.SECONDS);
    }

    @Override
    public void close() {
        _batchHealthChecksFuture.cancel(true);
        _hostDiscovery.removeListener(_hostDiscoveryListener);

        if (_shutdownHealthCheckExecutorOnClose) {
            _healthCheckExecutor.shutdownNow();
        }
    }

    @Override
    public <R> R execute(RetryPolicy retry, ServiceCallback<S, R> callback) {
        Stopwatch sw = new Stopwatch(_ticker).start();
        int numAttempts = 0;
        do {
            Iterable<ServiceEndPoint> hosts = _hostDiscovery.getHosts();
            if (Iterables.isEmpty(hosts)) {
                // There were no service endpoints available, we have no choice but to stop trying and just exit.
                throw new NoAvailableHostsException();
            }

            Iterable<ServiceEndPoint> goodHosts = Iterables.filter(hosts, _badEndpointFilter);
            if (Iterables.isEmpty(goodHosts)) {
                // All available hosts are bad, so we must give up.
                throw new OnlyBadHostsException();
            }

            ServiceEndPoint endpoint = _loadBalanceAlgorithm.choose(goodHosts);
            if (endpoint == null) {
                throw new NoSuitableHostsException();
            }

            S service = _serviceFactory.create(endpoint);
            try {
                return callback.call(service);
            } catch (Throwable t) {
                if (_exceptionMapper != null) {
                    t = _exceptionMapper.translate(t);
                }
                if (!(t instanceof ServiceException)) {
                    throw Throwables.propagate(t);
                }
                // This is a known and supported exception indicating that something went wrong somewhere in the service
                // layer while trying to communicate with the endpoint.  These errors are often transient, so we enqueue
                // a health check for the endpoint and mark it as unavailable for the time being.
                markEndpointAsBad(endpoint);
            }
        } while (retry.allowRetry(++numAttempts, sw.elapsedMillis()));

        throw new MaxRetriesException();
    }

    @Override
    public <R> Future<R> executeAsync(RetryPolicy retry, ServiceCallback<S, R> callback) {
        throw new UnsupportedOperationException();
    }

    @VisibleForTesting
    Set<ServiceEndPoint> getBadEndpoints() {
        return ImmutableSet.copyOf(_badEndpoints);
    }

    private synchronized void addEndpoint(ServiceEndPoint endpoint) {
        _recentlyRemovedEndpoints.remove(endpoint);
        _badEndpoints.remove(endpoint);
    }

    private synchronized void removeEndpoint(ServiceEndPoint endpoint) {
        // Mark this endpoint as recently removed.  We do this in order to keep a positive set of removed
        // endpoints so that we avoid a potential race condition where someone was using this endpoint while
        // we noticed it was disappeared from host discovery.  In that case there is the potential that they
        // would add it to the bad endpoints set after we've already processed the removal, thus leading to a
        // memory leak in the bad endpoints set.  Having this time-limited view of the recently removed
        // endpoints ensures that this memory leak doesn't happen.
        _recentlyRemovedEndpoints.add(endpoint);
        _badEndpoints.remove(endpoint);
    }

    private synchronized void markEndpointAsBad(ServiceEndPoint endpoint) {
        if (_recentlyRemovedEndpoints.contains(endpoint)) {
            // Nothing to do, we've already removed this endpoint
            return;
        }

        // Only schedule a health check if this is the first time we've seen this endpoint as bad...
        if (_badEndpoints.add(endpoint)) {
            _healthCheckExecutor.submit(new HealthCheck(endpoint));
        }
    }

    @VisibleForTesting
    final class HealthCheck implements Runnable {
        private final ServiceEndPoint _endpoint;

        public HealthCheck(ServiceEndPoint endpoint) {
            _endpoint = endpoint;
        }

        @Override
        public void run() {
            if (isHealthy(_endpoint)) {
                _badEndpoints.remove(_endpoint);
            }
        }
    }

    @VisibleForTesting
    final class BatchHealthChecks implements Runnable {
        @Override
        public void run() {
            for (ServiceEndPoint endpoint : _badEndpoints) {
                if (isHealthy(endpoint)) {
                    _badEndpoints.remove(endpoint);
                }

                // If we were interrupted during checking the health (but weren't blocked so an InterruptedException
                // couldn't be thrown), then we should exit now.
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }
            }
        }
    }

    @VisibleForTesting
    boolean isHealthy(ServiceEndPoint endpoint) {
        // We have to be very careful to not allow an exceptions to make it out of of this method, if they do then
        // subsequent scheduled invocations of the Runnables may not happen, and we could stop checking health checks
        // completely.  So we intentionally handle all possible exceptions here.
        try {
            return _serviceFactory.isHealthy(endpoint);
        } catch (Throwable ignored) {
            // If anything goes bad, we'll still consider the endpoint unhealthy.
            return false;
        }
    }
}
