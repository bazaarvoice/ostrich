package com.bazaarvoice.ostrich.pool;

import com.bazaarvoice.ostrich.PartitionContext;
import com.bazaarvoice.ostrich.RetryPolicy;
import com.bazaarvoice.ostrich.ServiceCallback;
import com.bazaarvoice.ostrich.ServiceEndPoint;
import com.bazaarvoice.ostrich.ServiceEndPointPredicate;
import com.bazaarvoice.ostrich.exceptions.MaxRetriesException;
import com.bazaarvoice.ostrich.exceptions.NoAvailableHostsException;
import com.bazaarvoice.ostrich.metrics.Metrics;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import com.google.common.base.Ticker;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collection;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Preconditions.checkNotNull;

class AsyncServicePool<S> implements com.bazaarvoice.ostrich.AsyncServicePool<S> {
    private static final Logger LOG = LoggerFactory.getLogger(AsyncServicePool.class);

    private static final ServiceEndPointPredicate ALL_END_POINTS = new ServiceEndPointPredicate() {
        @Override
        public boolean apply(ServiceEndPoint endPoint) {
            return true;
        }
    };

    private final Ticker _ticker;
    private final ServicePool<S> _pool;
    private final boolean _shutdownPoolOnClose;
    private final ExecutorService _executor;
    private final boolean _shutdownExecutorOnClose;
    private final Metrics.InstanceMetrics _metrics;
    private final Timer _executionTime;
    private final Meter _numExecuteSuccesses;
    private final Meter _numExecuteFailures;
    private final Histogram _executeBatchSize;

    AsyncServicePool(Ticker ticker, ServicePool<S> pool, boolean shutdownPoolOnClose,
                            ExecutorService executor, boolean shutdownExecutorOnClose, MetricRegistry metrics) {
        _ticker = checkNotNull(ticker);
        _pool = checkNotNull(pool);
        _shutdownPoolOnClose = shutdownPoolOnClose;
        _executor = checkNotNull(executor);
        _shutdownExecutorOnClose = shutdownExecutorOnClose;

        _metrics = Metrics.forInstance(metrics, this, _pool.getServiceName());
        _executionTime = _metrics.timer("execution-time");
        _numExecuteSuccesses = _metrics.meter("num-execute-successes");
        _numExecuteFailures = _metrics.meter("num-execute-failures");
        _executeBatchSize = _metrics.histogram("execute-batch-size");
    }

    @Override
    public void close() throws IOException {
        if (_shutdownExecutorOnClose) {
            _executor.shutdown();
        }

        if (_shutdownPoolOnClose) {
            _pool.close();
        }

        _metrics.close();
    }

    @Override
    public <R> Future<R> execute(final RetryPolicy retryPolicy, final ServiceCallback<S, R> callback) {
        return _executor.submit(new Callable<R>() {
            @Override
            public R call() throws Exception {
                return _pool.execute(retryPolicy, callback);
            }
        });
    }

    @Override
    public <R> Future<R> execute(final PartitionContext partitionContext, final RetryPolicy retryPolicy,
                                 final ServiceCallback<S, R> callback) {
        return _executor.submit(new Callable<R>() {
            @Override
            public R call() throws Exception {
                return _pool.execute(partitionContext, retryPolicy, callback);
            }
        });
    }

    @Override
    public <R> Collection<Future<R>> executeOnAll(RetryPolicy retry, ServiceCallback<S, R> callback) {
        return executeOn(ALL_END_POINTS, retry, callback);
    }

    @Override
    public <R> Collection<Future<R>> executeOn(ServiceEndPointPredicate predicate, final RetryPolicy retry,
                                               final ServiceCallback<S, R> callback) {
        Collection<Future<R>> futures = Lists.newArrayList();

        Iterable<ServiceEndPoint> endPoints = _pool.getAllEndPoints();
        if (Iterables.isEmpty(endPoints)) {
            throw new NoAvailableHostsException(String.format("No hosts discovered for service %s", _pool.getServiceName()));
        }

        for (final ServiceEndPoint endPoint : endPoints) {
            if (!predicate.apply(endPoint)) {
                continue;
            }

            Future<R> future = _executor.submit(new Callable<R>() {
                @Override
                public R call() throws Exception {
                    Timer.Context timer = _executionTime.time();
                    final long start = _ticker.read();
                    int numAttempts = 0;

                    try {
                        Exception lastException;

                        do {
                            try {
                                R result = _pool.executeOnEndPoint(endPoint, callback);
                                _numExecuteSuccesses.mark();
                                return result;
                            } catch (Exception e) {
                                _numExecuteFailures.mark();

                                // Don't retry if exception is too severe.
                                if (!_pool.isRetriableException(e)) {
                                    throw e;
                                }

                                lastException = e;
                                LOG.info("Retriable exception from end point: " + endPoint, e);
                            }
                        } while (retry.allowRetry(++numAttempts, TimeUnit.NANOSECONDS.toMillis(_ticker.read() - start)));

                        throw new MaxRetriesException(lastException);
                    } finally {
                        timer.stop();
                    }
                }
            });

            futures.add(future);
        }

        _executeBatchSize.update(futures.size());
        return futures;
    }

    @Override
    public int getNumValidEndPoints() {
        return _pool.getNumValidEndPoints();
    }

    @Override
    public int getNumBadEndPoints() {
        return _pool.getNumBadEndPoints();
    }
}
