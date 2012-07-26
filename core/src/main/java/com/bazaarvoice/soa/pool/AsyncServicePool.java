package com.bazaarvoice.soa.pool;

import com.bazaarvoice.soa.RetryPolicy;
import com.bazaarvoice.soa.ServiceCallback;
import com.bazaarvoice.soa.ServicePool;

import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import static com.google.common.base.Preconditions.checkNotNull;

public class AsyncServicePool<S> implements com.bazaarvoice.soa.AsyncServicePool<S> {
    private final ServicePool<S> _pool;
    private final boolean _shutdownPoolOnClose;
    private final ExecutorService _executor;
    private final boolean _shutdownExecutorOnClose;

    public AsyncServicePool(ServicePool<S> pool, boolean shutdownPoolOnClose,
                            ExecutorService executor, boolean shutdownExecutorOnClose) {
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
}
