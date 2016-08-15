package com.bazaarvoice.ostrich.pool;

import com.bazaarvoice.ostrich.HealthCheckResult;
import com.bazaarvoice.ostrich.HealthCheckResults;
import com.bazaarvoice.ostrich.HostDiscovery;
import com.bazaarvoice.ostrich.LoadBalanceAlgorithm;
import com.bazaarvoice.ostrich.PartitionContext;
import com.bazaarvoice.ostrich.PartitionContextBuilder;
import com.bazaarvoice.ostrich.RetryPolicy;
import com.bazaarvoice.ostrich.ServiceCallback;
import com.bazaarvoice.ostrich.ServiceEndPoint;
import com.bazaarvoice.ostrich.ServiceFactory;
import com.bazaarvoice.ostrich.ServicePoolStatistics;
import com.bazaarvoice.ostrich.exceptions.MaxRetriesException;
import com.bazaarvoice.ostrich.exceptions.NoAvailableHostsException;
import com.bazaarvoice.ostrich.exceptions.NoCachedInstancesAvailableException;
import com.bazaarvoice.ostrich.exceptions.NoSuitableHostsException;
import com.bazaarvoice.ostrich.exceptions.OnlyBadHostsException;
import com.bazaarvoice.ostrich.healthcheck.DefaultHealthCheckResults;
import com.bazaarvoice.ostrich.healthcheck.HealthCheckRetryDelay;
import com.bazaarvoice.ostrich.metrics.Metrics;
import com.bazaarvoice.ostrich.partition.PartitionFilter;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Objects;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.base.Throwables;
import com.google.common.base.Ticker;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static com.google.common.base.Preconditions.checkNotNull;

class ServicePool<S> implements com.bazaarvoice.ostrich.ServicePool<S> {
    private static final Logger LOG = LoggerFactory.getLogger(ServicePool.class);

    /**
     * Number of seconds between bad endpoint health check verification runs.
     */
    private static final int HEALTH_CHECK_VERIFY_SECS = 30;

    private final Ticker _ticker;
    private final HostDiscovery _hostDiscovery;
    private final boolean _cleanupHostDiscoveryOnClose;
    private final HostDiscovery.EndPointListener _hostDiscoveryListener;
    private final ServiceFactory<S> _serviceFactory;
    private final ScheduledExecutorService _healthCheckExecutor;
    private final boolean _shutdownHealthCheckExecutorOnClose;
    private final PartitionFilter _partitionFilter;
    private final LoadBalanceAlgorithm _loadBalanceAlgorithm;
    private final ServicePoolStatistics _servicePoolStatistics;
    private final ConcurrentMap<ServiceEndPoint, HealthCheck> _badEndPoints;
    private final Predicate<ServiceEndPoint> _badEndPointFilter;
    private final Set<ServiceEndPoint> _recentlyRemovedEndPoints;
    private final ServiceCache<S> _serviceCache;
    private final Metrics.InstanceMetrics _metrics;
    private final Timer _callbackExecutionTime;
    private final Timer _healthCheckTime;
    private final Meter _numExecuteSuccesses;
    private final Meter _numExecuteAttemptFailures;
    private final HealthCheckRetryDelay _healthCheckRetryDelay;

    ServicePool(Ticker ticker, HostDiscovery hostDiscovery, boolean cleanupHostDiscoveryOnClose,
                ServiceFactory<S> serviceFactory, ServiceCachingPolicy cachingPolicy,
                PartitionFilter partitionFilter, LoadBalanceAlgorithm loadBalanceAlgorithm,
                ScheduledExecutorService healthCheckExecutor, boolean shutdownHealthCheckExecutorOnClose,
                HealthCheckRetryDelay healthCheckRetryDelay, MetricRegistry metrics) {
        _healthCheckRetryDelay = checkNotNull(healthCheckRetryDelay);
        _ticker = checkNotNull(ticker);
        _hostDiscovery = checkNotNull(hostDiscovery);
        _cleanupHostDiscoveryOnClose = cleanupHostDiscoveryOnClose;
        _serviceFactory = checkNotNull(serviceFactory);
        _healthCheckExecutor = checkNotNull(healthCheckExecutor);
        _shutdownHealthCheckExecutorOnClose = shutdownHealthCheckExecutorOnClose;
        _badEndPoints = Maps.newConcurrentMap();
        _badEndPointFilter = Predicates.not(Predicates.in(_badEndPoints.keySet()));
        _recentlyRemovedEndPoints = Sets.newSetFromMap(CacheBuilder.newBuilder()
                .ticker(_ticker)
                .expireAfterWrite(10, TimeUnit.MINUTES)  // TODO: Make this a constant
                .<ServiceEndPoint, Boolean>build()
                .asMap());
        checkNotNull(cachingPolicy);
        _serviceCache = new ServiceCacheBuilder<S>()
                .withServiceFactory(serviceFactory)
                .withCachingPolicy(cachingPolicy)
                .withMetricRegistry(metrics)
                .build();
        _partitionFilter = checkNotNull(partitionFilter);
        _loadBalanceAlgorithm = checkNotNull(loadBalanceAlgorithm);

        _servicePoolStatistics = new ServicePoolStatistics() {
            @Override
            public int getNumIdleCachedInstances(ServiceEndPoint endPoint) {
                return _serviceCache.getNumIdleInstances(endPoint);
            }

            @Override
            public int getNumActiveInstances(ServiceEndPoint endPoint) {
                return _serviceCache.getNumActiveInstances(endPoint);
            }
        };

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

        _metrics = Metrics.forInstance(metrics, this, _serviceFactory.getServiceName());
        _callbackExecutionTime = _metrics.timer("callback-execution-time");
        _healthCheckTime = _metrics.timer("health-check-time");
        _numExecuteSuccesses = _metrics.meter("num-execute-successes");
        _numExecuteAttemptFailures = _metrics.meter("num-execute-attempt-failures");
        _metrics.gauge("num-valid-end-points", new Gauge<Integer>() {
            @Override
            public Integer getValue() {
                return getNumValidEndPoints();
            }
        });
        _metrics.gauge("num-bad-end-points", new Gauge<Integer>() {
            @Override
            public Integer getValue() {
                return getNumBadEndPoints();
            }
        });

        // Periodically ensure that health checks for unhealthy endpoints are still running
        _healthCheckExecutor.scheduleAtFixedRate(
                new HealthCheckVerifier(),
                HEALTH_CHECK_VERIFY_SECS,
                HEALTH_CHECK_VERIFY_SECS,
                TimeUnit.SECONDS);
    }

    @Override
    public void close() {
        for (HealthCheck healthCheck : _badEndPoints.values()) {
            healthCheck.cancel(true);
        }

        _hostDiscovery.removeListener(_hostDiscoveryListener);
        if (_cleanupHostDiscoveryOnClose) {
            try {
                _hostDiscovery.close();
            } catch (IOException e) {
                // NOP
            }
        }

        _serviceCache.close();
        _metrics.close();

        if (_shutdownHealthCheckExecutorOnClose) {
            _healthCheckExecutor.shutdownNow();
        }
    }

    @Override
    public <R> R execute(RetryPolicy retry, ServiceCallback<S, R> callback) {
        return execute(PartitionContextBuilder.empty(), retry, callback);
    }

    @Override
    public <R> R execute(PartitionContext partitionContext, RetryPolicy retry, ServiceCallback<S, R> callback) {
        final long start = _ticker.read();
        int numAttempts = 0;
        Exception lastException = null;

        do {
            Iterable<ServiceEndPoint> allEndPoints = getAllEndPoints();
            if (Iterables.isEmpty(allEndPoints)) {
                throw (lastException == null)
                        ? new NoAvailableHostsException()
                        : new NoAvailableHostsException(lastException);
            }

            Iterable<ServiceEndPoint> validEndPoints = getValidEndPoints(allEndPoints);
            if (Iterables.isEmpty(validEndPoints)) {
                throw (lastException == null)
                        ? new OnlyBadHostsException()
                        : new OnlyBadHostsException(lastException);
            }

            ServiceEndPoint endPoint = chooseEndPoint(validEndPoints, partitionContext);
            if (endPoint == null) {
                throw (lastException == null)
                        ? new NoSuitableHostsException()
                        : new NoSuitableHostsException(lastException);
            }

            try {
                R result = executeOnEndPoint(endPoint, callback);
                _numExecuteSuccesses.mark();
                return result;
            } catch (Exception e) {
                _numExecuteAttemptFailures.mark();

                // Don't retry if exception is too severe.
                if (!isRetriableException(e)) {
                    throw Throwables.propagate(e);
                }

                LOG.info("Retriable exception from end point id: {}, {}", endPoint.getId(), e.toString());
                LOG.debug("Exception", e);
                lastException = e;
            }
        }
        while (retry.allowRetry(++numAttempts, TimeUnit.NANOSECONDS.toMillis(_ticker.read() - start)));

        throw new MaxRetriesException(lastException);
    }

    @Override
    public int getNumValidEndPoints() {
        return Iterables.size(_hostDiscovery.getHosts()) - _badEndPoints.size();
    }

    @Override
    public int getNumBadEndPoints() {
        return _badEndPoints.size();
    }

    /**
     * Determine the set of all {@link ServiceEndPoint}s.
     * <p/>
     * NOTE: This method is package private specifically so that {@link AsyncServicePool} can call it.
     */
    Iterable<ServiceEndPoint> getAllEndPoints() {
        return _hostDiscovery.getHosts();
    }

    /**
     * Determine the set of usable {@link ServiceEndPoint}s.
     */
    private Iterable<ServiceEndPoint> getValidEndPoints(Iterable<ServiceEndPoint> endPoints) {
        return Iterables.filter(endPoints, _badEndPointFilter);
    }

    private ServiceEndPoint chooseEndPoint(Iterable<ServiceEndPoint> endPoints, PartitionContext partitionContext) {
        endPoints = _partitionFilter.filter(endPoints, partitionContext);

        if (endPoints == null || Iterables.isEmpty(endPoints)) {
            return null;
        }

        ServiceEndPoint endPoint = _loadBalanceAlgorithm.choose(endPoints, _servicePoolStatistics);
        if (endPoint == null) {
            return null;
        }

        return endPoint;
    }

    /**
     * Execute a callback on a specific end point.
     * <p/>
     * NOTE: This method is package private specifically so that {@link AsyncServicePool} can call it.
     */
    <R> R executeOnEndPoint(ServiceEndPoint endPoint, ServiceCallback<S, R> callback)
            throws Exception {
        ServiceHandle<S> handle = null;

        try {
            handle = _serviceCache.checkOut(endPoint);

            Timer.Context timer = _callbackExecutionTime.time();
            try {
                return callback.call(handle.getService());
            } finally {
                timer.stop();
            }
        } catch (NoCachedInstancesAvailableException e) {
            LOG.debug("Service cache exhausted. End point ID: {}", endPoint.getId(), e);
            // Don't mark an end point as bad just because there are no cached end points for it.
            throw e;
        } catch (Exception e) {
            if (_serviceFactory.isRetriableException(e)) {
                // This is a known and supported exception indicating that something went wrong somewhere in the service
                // layer while trying to communicate with the end point.  These errors are often transient, so we
                // enqueue a health check for the end point and mark it as unavailable for the time being.
                markEndPointAsBad(endPoint);
                LOG.debug("Bad end point discovered. End point ID: {}", endPoint.getId(), e);
            }
            throw e;
        } finally {
            if (handle != null) {
                try {
                    _serviceCache.checkIn(handle);
                } catch (Exception e) {
                    // This should never happen, but log just in case.
                    LOG.warn("Error returning end point to cache. End point ID: {}, {}",
                            endPoint.getId(), e.toString());
                    LOG.debug("Exception", e);
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

    /**
     * NOTE: This method is package private specifically so that {@link AsyncServicePool} can call it.
     *
     * @return The name of the service for this pool.
     */
    String getServiceName() {
        return _serviceFactory.getServiceName();
    }

    @VisibleForTesting
    HostDiscovery getHostDiscovery() {
        return _hostDiscovery;
    }

    @VisibleForTesting
    PartitionFilter getPartitionFilter() {
        return _partitionFilter;
    }

    @VisibleForTesting
    LoadBalanceAlgorithm getLoadBalanceAlgorithm() {
        return _loadBalanceAlgorithm;
    }

    @VisibleForTesting
    ServicePoolStatistics getServicePoolStatistics() {
        return _servicePoolStatistics;
    }

    @VisibleForTesting
    Set<ServiceEndPoint> getBadEndPoints() {
        return ImmutableSet.copyOf(_badEndPoints.keySet());
    }

    @Override
    public HealthCheckResults checkForHealthyEndPoint() {
        DefaultHealthCheckResults aggregate = new DefaultHealthCheckResults();

        Iterable<ServiceEndPoint> allEndPoints = getAllEndPoints();
        if (Iterables.isEmpty(allEndPoints)) {
            // There were no end points
            return aggregate;
        }

        Iterable<ServiceEndPoint> validEndPoints = getValidEndPoints(allEndPoints);
        if (Iterables.isEmpty(validEndPoints)) {
            // There were no valid end points
            return aggregate;
        }

        Set<ServiceEndPoint> endPoints = Sets.newHashSet(validEndPoints);
        while (!endPoints.isEmpty()) {
            // Prefer end points in the order the load balancer recommends.
            ServiceEndPoint endPoint = chooseEndPoint(endPoints, PartitionContextBuilder.empty());
            if (endPoint == null) {
                // Load balancer didn't like our end points, so just go sequentially.
                endPoint = endPoints.iterator().next();
            }

            HealthCheckResult result = checkHealth(endPoint);
            aggregate.addHealthCheckResult(result);

            if (!result.isHealthy()) {
                Exception exception = ((FailedHealthCheckResult) result).getException();
                if (exception == null || isRetriableException(exception)) {
                    LOG.debug("Unhealthy end point discovered. End point ID: {}", endPoint.getId());
                    endPoints.remove(endPoint);
                    markEndPointAsBad(endPoint);
                    continue;
                }
            }

            break;
        }

        return aggregate;
    }

    /**
     * Run the health checks on all current unhealthy end points. This method blocks until the
     * health checks have completed.
     */
    @VisibleForTesting
    void forceHealthChecks() {
        for (HealthCheck healthCheck : _badEndPoints.values()) {
            healthCheck.run();
        }
    }

    private synchronized void addEndPoint(ServiceEndPoint endPoint) {
        // submit calls schedule with 0 delay
        _healthCheckExecutor.submit( new EndPointAdditionProcessor( endPoint ) );
    }

    private final class EndPointAdditionProcessor implements Runnable {

        private final ServiceEndPoint endPoint;

        private EndPointAdditionProcessor( final ServiceEndPoint endPoint ) {
            this.endPoint = endPoint;
        }

        @Override
        public void run() {
            HealthCheckResult result = checkHealth( endPoint );
            if(result.isHealthy()) {
                synchronized ( ServicePool.this ) {
                    _serviceCache.register( endPoint );
                    _recentlyRemovedEndPoints.remove( endPoint );
                    _badEndPoints.remove( endPoint );
                }
                LOG.debug("End point added to service pool. End point ID: {}", endPoint.getId());
            }
            else {
                synchronized ( ServicePool.this ) {
                    // not calling markEndPointAsBad as it skips the flow if this endPoint is recently
                    // removed, which defeats the purpose of adding an endPoint which was rediscovered
                    // as a good endPoint after being removed as bad
                    // only start a fresh health check if the node is new/not marked as bad already
                    if(! _badEndPoints.containsKey( endPoint )) {
                        HealthCheck healthCheck = new HealthCheck(endPoint);
                        _badEndPoints.put(endPoint, healthCheck);
                        healthCheck.start(); // this does not start a health check, just submits a scheduled health check
                    }
                    // else let ostrich decide when to health check and bring back the node
                }
                LOG.debug("Unhealthy end point added to badEndPoints for later addition to service pool. End point ID: {}", endPoint.getId());
            }
        }
    }

    private synchronized void removeEndPoint(ServiceEndPoint endPoint) {
        // Mark this end point as recently removed.  We do this in order to keep a positive set of removed
        // end points so that we avoid a potential race condition where someone was using this end point while
        // we noticed it disappeared from host discovery.  In that case there is the potential that they
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
        HealthCheck healthCheck = new HealthCheck(endPoint);
        if (_badEndPoints.putIfAbsent(endPoint, healthCheck) == null) {
            healthCheck.start();
        }
    }

    @VisibleForTesting
    HealthCheckResult checkHealth(ServiceEndPoint endPoint) {
        // We have to be very careful to not allow any exceptions to make it out of of this method, if they do then
        // subsequent scheduled invocations of the Runnable may not happen, and we could stop checking health checks
        // completely.  So we intentionally handle all possible exceptions here.
        final long start = _ticker.read();
        boolean isHealthy;
        Exception exception = null;

        try {
            isHealthy = _serviceFactory.isHealthy(endPoint);
        } catch (Exception e) {
            isHealthy = false;
            exception = e;
        }

        final long duration = _ticker.read() - start;
        _healthCheckTime.update(duration, TimeUnit.NANOSECONDS);
        return isHealthy
                ? new SuccessfulHealthCheckResult(endPoint.getId(), duration)
                : new FailedHealthCheckResult(endPoint.getId(), duration, exception);
    }

    @VisibleForTesting
    final class HealthCheckVerifier implements Runnable {
        @Override
        public void run() {
            try {
                for (HealthCheck healthCheck : _badEndPoints.values()) {
                    healthCheck.verifyScheduling();
                }
            } catch (Throwable ex) {
                LOG.warn("Error rescheduling health checks", ex);
            }
        }
    }

    @VisibleForTesting
    final class HealthCheck implements Runnable {
        private final ServiceEndPoint _endPoint;
        private final Lock _lock = new ReentrantLock();
        private int _count = 0;
        private Future _future;
        private boolean _cancelled;
        private boolean _scheduled;
        private boolean _running;

        public HealthCheck(ServiceEndPoint endPoint) {
            _endPoint = endPoint;
        }

        public void start() {
            _lock.lock();
            try {
                assert _future == null && !_cancelled;
                _future = _healthCheckExecutor.submit(this);
                _scheduled = true;
            } finally {
                _lock.unlock();
            }
        }

        public void cancel(boolean mayInterruptIfRunning) {
            _lock.lock();
            try {
                if (_future != null) {
                    _future.cancel(mayInterruptIfRunning);
                    _future = null;
                }

                _cancelled = true;
            } finally {
                _lock.unlock();
            }
        }

        public void verifyScheduling() {
            _lock.lock();
            try {
                if (!_cancelled && !_running && !_scheduled) {
                    _healthCheckExecutor.submit(this);
                    _scheduled = true;
                }
            } finally {
                _lock.unlock();
            }
        }

        @Override
        public void run() {
            _lock.lock();
            try {
                if (_cancelled || _badEndPoints.get(_endPoint) != this) {
                    return;
                }

                _scheduled = false;
                _running = true;

                // Don't perform health check operation in lock as it could cause deadlock on cancel if
                // health check stalls.
                HealthCheckResult result;
                _lock.unlock();
                try {
                    result = checkHealth(_endPoint);
                } finally {
                    _lock.lock();
                }

                _count += 1;

                if (result.isHealthy()) {
                    _serviceCache.register(_endPoint);
                    _badEndPoints.remove(_endPoint, this);
                    this.cancel(false);
                } else {
                    long delayMillis = _healthCheckRetryDelay.getDelay(_count, result);

                    if (_future != null) {
                        _future.cancel(false); // In case this Runnable was invoked directly and not by scheduler
                    }

                    _future = _healthCheckExecutor.schedule(this, Math.max(0, delayMillis), TimeUnit.MILLISECONDS);
                    _scheduled = true;
                }
            } finally {
                _running = false;
                _lock.unlock();
            }
        }
    }

    private static final class SuccessfulHealthCheckResult implements HealthCheckResult {
        private final String _endPointId;
        private final long _responseTimeInNanos;

        public SuccessfulHealthCheckResult(String endPointId, long responseTimeInNanos) {
            _endPointId = endPointId;
            _responseTimeInNanos = responseTimeInNanos;
        }

        @Override
        public boolean isHealthy() {
            return true;
        }

        @Override
        public String getEndPointId() {
            return _endPointId;
        }

        @Override
        public long getResponseTime(TimeUnit unit) {
            return unit.convert(_responseTimeInNanos, TimeUnit.NANOSECONDS);
        }

        @Override
        public String toString() {
            return Objects.toStringHelper(this)
                    .add("endPointId", _endPointId)
                    .toString();
        }
    }

    private static final class FailedHealthCheckResult implements HealthCheckResult {
        private final String _endPointId;
        private final long _responseTimeInNanos;
        private final Exception _exception;

        public FailedHealthCheckResult(String endPointId, long responseTimeInNanos, Exception exception) {
            _endPointId = endPointId;
            _responseTimeInNanos = responseTimeInNanos;
            _exception = exception;
        }

        public FailedHealthCheckResult(String endPointId, long responseTimeInNanos) {
            this(endPointId, responseTimeInNanos, null);
        }

        @Override
        public boolean isHealthy() {
            return false;
        }

        @Override
        public String getEndPointId() {
            return _endPointId;
        }

        @Override
        public long getResponseTime(TimeUnit unit) {
            return unit.convert(_responseTimeInNanos, TimeUnit.NANOSECONDS);
        }

        public Exception getException() {
            return _exception;
        }

        @Override
        public String toString() {
            return Objects.toStringHelper(this)
                    .add("endPointId", _endPointId)
                    .add("exception", _exception)
                    .toString();
        }
    }
}
