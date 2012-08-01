package com.bazaarvoice.soa.pool;

import com.bazaarvoice.soa.RetryPolicy;
import com.bazaarvoice.soa.ServiceCallback;
import com.bazaarvoice.soa.ServiceEndPoint;
import com.bazaarvoice.soa.ServiceEndPointPredicate;
import com.bazaarvoice.soa.exceptions.MaxRetriesException;
import com.google.common.base.Stopwatch;
import com.google.common.base.Ticker;
import com.google.common.collect.Lists;

import java.io.IOException;
import java.util.Collection;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import static com.google.common.base.Preconditions.checkNotNull;

class AsyncServicePool<S> implements com.bazaarvoice.soa.AsyncServicePool<S> {
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

    AsyncServicePool(Ticker ticker, ServicePool<S> pool, boolean shutdownPoolOnClose,
                            ExecutorService executor, boolean shutdownExecutorOnClose) {
        _ticker = checkNotNull(ticker);
        _pool = checkNotNull(pool);
        _shutdownPoolOnClose = shutdownPoolOnClose;
        _executor = checkNotNull(executor);
        _shutdownExecutorOnClose = shutdownExecutorOnClose;
    }

    @Override
    public void close() throws IOException {
        if (_shutdownExecutorOnClose) {
            _executor.shutdown();
        }

        if (_shutdownPoolOnClose) {
            _pool.close();
        }
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
    public <R> Collection<Future<R>> executeOnAll(RetryPolicy retry, ServiceCallback<S, R> callback) {
        return executeOn(ALL_END_POINTS, retry, callback);
    }

    @Override
    public <R> Collection<Future<R>> executeOn(ServiceEndPointPredicate predicate, final RetryPolicy retry,
                                               final ServiceCallback<S, R> callback) {
        Collection<Future<R>> futures = Lists.newArrayList();

        for (final ServiceEndPoint endPoint : _pool.getAllEndPoints()) {
            if (!predicate.apply(endPoint)) {
                continue;
            }

            Future<R> future = _executor.submit(new Callable<R>() {
                @Override
                public R call() throws Exception {
                    Stopwatch sw = new Stopwatch(_ticker).start();
                    int numAttempts = 0;
                    do {
                        try {
                            return _pool.executeOnEndPoint(endPoint, callback);
                        } catch (Exception e) {
                            // Don't retry if exception is too severe.
                            if (!_pool.isRetriableException(e)) {
                                throw e;
                            }
                        }
                    } while (retry.allowRetry(++numAttempts, sw.elapsedMillis()));

                    throw new MaxRetriesException();
                }
            });

            futures.add(future);
        }

        return futures;
    }
}
