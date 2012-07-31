package com.bazaarvoice.soa.pool;

import com.bazaarvoice.soa.HostDiscovery;
import com.bazaarvoice.soa.LoadBalanceAlgorithm;
import com.bazaarvoice.soa.RetryPolicy;
import com.bazaarvoice.soa.ServiceCallback;
import com.bazaarvoice.soa.ServiceEndPoint;
import com.bazaarvoice.soa.ServiceFactory;
import com.bazaarvoice.soa.ServicePoolStatistics;
import com.bazaarvoice.soa.exceptions.MaxRetriesException;
import com.bazaarvoice.soa.exceptions.NoAvailableHostsException;
import com.bazaarvoice.soa.exceptions.NoCachedInstancesAvailableException;
import com.bazaarvoice.soa.exceptions.NoSuitableHostsException;
import com.bazaarvoice.soa.exceptions.OnlyBadHostsException;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.helpers.MessageFormatter;

import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Preconditions.checkNotNull;

class ServicePool<S> implements com.bazaarvoice.soa.ServicePool<S> {
    private static final Logger LOG = LoggerFactory.getLogger(ServicePool.class);

    // By default check every minute to see if a previously unhealthy end point has become healthy.
    @VisibleForTesting
    static final long HEALTH_CHECK_POLL_INTERVAL_IN_SECONDS = 60;

    private final Ticker _ticker;
    private final HostDiscovery _hostDiscovery;
    private final HostDiscovery.EndPointListener _hostDiscoveryListener;
    private final ServiceFactory<S> _serviceFactory;
    private final ScheduledExecutorService _healthCheckExecutor;
    private final boolean _shutdownHealthCheckExecutorOnClose;
    private final LoadBalanceAlgorithm _loadBalanceAlgorithm;
    private final Set<ServiceEndPoint> _badEndPoints;
    private final Predicate<ServiceEndPoint> _badEndPointFilter;
    private final Set<ServiceEndPoint> _recentlyRemovedEndPoints;
    private final Future<?> _batchHealthChecksFuture;
    private final ServiceCache<S> _serviceCache;

    ServicePool(Ticker ticker, HostDiscovery hostDiscovery,
                ServiceFactory<S> serviceFactory, ServiceCachingPolicy cachingPolicy,
                ScheduledExecutorService healthCheckExecutor, boolean shutdownHealthCheckExecutorOnClose) {
        _ticker = checkNotNull(ticker);
        _hostDiscovery = checkNotNull(hostDiscovery);
        _serviceFactory = checkNotNull(serviceFactory);
        _healthCheckExecutor = checkNotNull(healthCheckExecutor);
        _shutdownHealthCheckExecutorOnClose = shutdownHealthCheckExecutorOnClose;
        _badEndPoints = Sets.newSetFromMap(Maps.<ServiceEndPoint, Boolean>newConcurrentMap());
        _badEndPointFilter = Predicates.not(Predicates.in(_badEndPoints));
        _recentlyRemovedEndPoints = Sets.newSetFromMap(CacheBuilder.newBuilder()
                .ticker(_ticker)
                .expireAfterWrite(10, TimeUnit.MINUTES)  // TODO: Make this a constant
                .<ServiceEndPoint, Boolean>build()
                .asMap());
        checkNotNull(cachingPolicy);
        _serviceCache = new ServiceCache<S>(cachingPolicy, serviceFactory);
        _loadBalanceAlgorithm = checkNotNull(_serviceFactory.getLoadBalanceAlgorithm(new ServicePoolStatistics() {
            @Override
            public int getNumIdleCachedInstances(ServiceEndPoint endPoint) {
                return _serviceCache.getNumIdleInstances(endPoint);
            }

            @Override
            public int getNumActiveInstances(ServiceEndPoint endPoint) {
                return _serviceCache.getNumActiveInstances(endPoint);
            }
        }));

        // Watch end points as they are removed from host discovery so that we can remove them from our set of bad
        // end points as well.  This will prevent the bad end points set from growing in an unbounded fashion.
        // There is a minor race condition that could happen here, but it's not anything to be concerned about.  The
        // HostDiscovery component could lose its connection to its backing data store and then immediately regain it
        // right afterwards.  If that happens it could remove all of its end points only to re-add them right back again
        // and we will "forget" that an end point was bad and try to use it again.  This isn't fatal though because
        // we'll just rediscover that it's a bad end point again in the future.  Also in the future it might be useful
        // to measure how long an end point has been considered bad and potentially take action for end points that are
        // bad for long periods of time.
        _hostDiscoveryListener = new HostDiscovery.EndPointListener() {
            @Override
            public void onEndPointAdded(ServiceEndPoint endPoint) {
                addEndPoint(endPoint);
            }

            @Override
            public void onEndPointRemoved(ServiceEndPoint endPoint) {
                removeEndPoint(endPoint);
            }
        };
        _hostDiscovery.addListener(_hostDiscoveryListener);

        // Periodically wake up and check any bad end points to see if they're now healthy.
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
            Iterable<ServiceEndPoint> goodHosts = getValidEndPoints();
            ServiceEndPoint endPoint = _loadBalanceAlgorithm.choose(goodHosts);
            if (endPoint == null) {
                throw new NoSuitableHostsException();
            }

            try {
                return executeOnEndPoint(endPoint, callback);
            } catch (Exception e) {
                // Don't retry if exception is too severe.
                if (!isRetriableException(e)) {
                    throw Throwables.propagate(e);
                }
            }
        } while (retry.allowRetry(++numAttempts, sw.elapsedMillis()));

        throw new MaxRetriesException();
    }

    /**
     * Determine the set of all {@link ServiceEndPoint}s.
     * <p/>
     * NOTE: This method is package private specifically so that {@link AsyncServicePool} can call it.
     */
    Iterable<ServiceEndPoint> getAllEndPoints() {
        Iterable<ServiceEndPoint> hosts = _hostDiscovery.getHosts();
        if (Iterables.isEmpty(hosts)) {
            // There were no service end points available, we have no choice but to stop trying and just exit.
            throw new NoAvailableHostsException();
        }

        return hosts;
    }

    /**
     * Determine the set of usable {@link ServiceEndPoint}s.
     * <p/>
     * NOTE: This method is package private specifically so that {@link AsyncServicePool} can call it.
     */
    Iterable<ServiceEndPoint> getValidEndPoints() {
        Iterable<ServiceEndPoint> goodHosts = Iterables.filter(getAllEndPoints(), _badEndPointFilter);
        if (Iterables.isEmpty(goodHosts)) {
            // All available hosts are bad, so we must give up.
            throw new OnlyBadHostsException();
        }

        return goodHosts;
    }

    /**
     * Execute a callback on a specific end point.
     * <p/>
     * NOTE: This method is package private specifically so that {@link AsyncServicePool} can call it.
     */
    <R> R executeOnEndPoint(ServiceEndPoint endPoint, ServiceCallback<S, R> callback) throws Exception {
        S service = null;

        try {
            service = _serviceCache.checkOut(endPoint);
            return callback.call(service);
        } catch (NoCachedInstancesAvailableException e) {
            LOG.info(MessageFormatter.format("Service cache exhausted. End point ID: {}", endPoint.getId())
                             .getMessage(),
                         e);
            // Don't mark an end point as bad just because there are no cached end points for it.
            throw e;
        } catch (Exception e) {
            if (_serviceFactory.isRetriableException(e)) {
                // This is a known and supported exception indicating that something went wrong somewhere in the service
                // layer while trying to communicate with the end point.  These errors are often transient, so we
                // enqueue a health check for the end point and mark it as unavailable for the time being.
                markEndPointAsBad(endPoint);
                LOG.info(MessageFormatter.format("Bad end point discovered. End point ID: {}", endPoint.getId())
                             .getMessage(),
                         e);
            }
            throw e;
        } finally {
            if (service != null) {
                try {
                    _serviceCache.checkIn(endPoint, service);
                } catch (Exception e) {
                    // This should never happen, but log just in case.
                    LOG.error(MessageFormatter.format("Error end point into cache. End point ID: {}", endPoint.getId())
                                  .getMessage(),
                              e);
                }
            }
        }
    }

    /**
     * Check if an exception is retriable.
     * </p>
     * NOTE: This method is package private specifically so that {@link AsyncServicePool} can call it.
     */
    boolean isRetriableException(Exception exception) {
        return _serviceFactory.isRetriableException(exception);
    }

    @VisibleForTesting
    HostDiscovery getHostDiscovery() {
        return _hostDiscovery;
    }

    @VisibleForTesting
    Set<ServiceEndPoint> getBadEndPoints() {
        return ImmutableSet.copyOf(_badEndPoints);
    }

    private synchronized void addEndPoint(ServiceEndPoint endPoint) {
        _recentlyRemovedEndPoints.remove(endPoint);
        _badEndPoints.remove(endPoint);
        LOG.debug("End point added to service pool. End point ID: {}", endPoint.getId());
    }

    private synchronized void removeEndPoint(ServiceEndPoint endPoint) {
        // Mark this end point as recently removed.  We do this in order to keep a positive set of removed
        // end points so that we avoid a potential race condition where someone was using this end point while
        // we noticed it was disappeared from host discovery.  In that case there is the potential that they
        // would add it to the bad end points set after we've already processed the removal, thus leading to a
        // memory leak in the bad end points set.  Having this time-limited view of the recently removed
        // end points ensures that this memory leak doesn't happen.
        _recentlyRemovedEndPoints.add(endPoint);
        _badEndPoints.remove(endPoint);
        _serviceCache.evict(endPoint);
        LOG.debug("End point removed from service pool. End point ID: {}", endPoint.getId());
    }

    private synchronized void markEndPointAsBad(ServiceEndPoint endPoint) {
        if (_recentlyRemovedEndPoints.contains(endPoint)) {
            // Nothing to do, we've already removed this end point
            return;
        }

        _serviceCache.evict(endPoint);

        // Only schedule a health check if this is the first time we've seen this end point as bad...
        if (_badEndPoints.add(endPoint)) {
            _healthCheckExecutor.submit(new HealthCheck(endPoint));
        }
    }

    @VisibleForTesting
    final class HealthCheck implements Runnable {
        private final ServiceEndPoint _endPoint;

        public HealthCheck(ServiceEndPoint endPoint) {
            _endPoint = endPoint;
        }

        @Override
        public void run() {
            if (isHealthy(_endPoint)) {
                _badEndPoints.remove(_endPoint);
            }
        }
    }

    @VisibleForTesting
    final class BatchHealthChecks implements Runnable {
        @Override
        public void run() {
            for (ServiceEndPoint endPoint : _badEndPoints) {
                if (isHealthy(endPoint)) {
                    _badEndPoints.remove(endPoint);
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
    boolean isHealthy(ServiceEndPoint endPoint) {
        // We have to be very careful to not allow an exceptions to make it out of of this method, if they do then
        // subsequent scheduled invocations of the Runnables may not happen, and we could stop checking health checks
        // completely.  So we intentionally handle all possible exceptions here.
        try {
            boolean healthy = _serviceFactory.isHealthy(endPoint);
            LOG.info("Health check status: {}; End point ID: {}", healthy ? "healthy" : "unhealthy", endPoint.getId());
            return healthy;
        } catch (Throwable ignored) {
            LOG.info(MessageFormatter.format("Health check status: error; End point ID: {}", endPoint.getId())
                    .getMessage(), ignored);
            // If anything goes bad, we'll still consider the end point unhealthy.
            return false;
        }
    }
}
