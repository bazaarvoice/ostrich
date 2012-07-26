package com.bazaarvoice.soa.pool;

import com.bazaarvoice.soa.RetryPolicy;
import com.bazaarvoice.soa.ServiceCallback;
import com.bazaarvoice.soa.ServicePool;
import com.google.common.collect.Lists;
import com.google.common.io.Closeables;
import com.google.common.util.concurrent.MoreExecutors;
import org.junit.After;
import org.junit.Test;

import java.io.IOException;
import java.util.Collection;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

public class AsyncServicePoolTest {
    private static final RetryPolicy NEVER_RETRY = mock(RetryPolicy.class);

    @SuppressWarnings("unchecked")
    private final ServicePool<Service> _mockPool = mock(ServicePool.class);

    private final ExecutorService _mockExecutor = mock(ExecutorService.class);
    private final Collection<AsyncServicePool<Service>> _asyncServicePools = Lists.newArrayList();

    @After
    public void teardown() {
        for (AsyncServicePool<Service> pool : _asyncServicePools) {
            Closeables.closeQuietly(pool);
        }
    }

    @Test(expected = NullPointerException.class)
    public void testNullServicePool() {
        new AsyncServicePool<Service>(null, true, _mockExecutor, true);
    }

    @SuppressWarnings("unchecked")
    @Test(expected = NullPointerException.class)
    public void testNullExecutorService() {
        new AsyncServicePool<Service>(_mockPool, true, null, true);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testSubmitsToExecutor() {
        AsyncServicePool<Service> pool = newAsyncPool();
        pool.execute(NEVER_RETRY, mock(ServiceCallback.class));

        verify(_mockExecutor).submit(any(Callable.class));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testExecutesCallbackInPool() {
        // Use a real executor so that it can actually call into the callback
        AsyncServicePool<Service> pool = newAsyncPool(MoreExecutors.sameThreadExecutor());

        ServiceCallback<Service, Void> callback = (ServiceCallback<Service, Void>) mock(ServiceCallback.class);
        pool.execute(NEVER_RETRY, callback);

        verify(_mockPool).execute(same(NEVER_RETRY), same(callback));
    }

    @Test
    public void testCloseDoesShutdownExecutor() throws IOException {
        AsyncServicePool<Service> pool = newAsyncPool(_mockExecutor, true);
        pool.close();

        verify(_mockExecutor).shutdown();
    }

    @Test
    public void testCloseDoesNotShutdownExecutor() throws IOException {
        AsyncServicePool<Service> pool = newAsyncPool(_mockExecutor, false);
        pool.close();

        verify(_mockExecutor, never()).shutdown();
    }

    @Test
    public void testCloseDoesShutdownPool() throws IOException {
        AsyncServicePool<Service> pool = newAsyncPool(_mockPool, true);
        pool.close();

        verify(_mockPool).close();
    }

    @Test
    public void testCloseDoesNotShutdownPool() throws IOException {
        AsyncServicePool<Service> pool = newAsyncPool(_mockPool, false);
        pool.close();

        verify(_mockPool, never()).close();
    }

    private AsyncServicePool<Service> newAsyncPool() {
        return newAsyncPool(_mockExecutor);
    }

    private AsyncServicePool<Service> newAsyncPool(ExecutorService executor) {
        return newAsyncPool(executor, true);
    }

    private AsyncServicePool<Service> newAsyncPool(ExecutorService executor, boolean shutdownExecutorOnClose) {
        AsyncServicePool<Service> pool = new AsyncServicePool<Service>(_mockPool, true, executor, shutdownExecutorOnClose);
        _asyncServicePools.add(pool);
        return pool;
    }

    private AsyncServicePool<Service> newAsyncPool(ServicePool<Service> pool, boolean shutdownPoolOnClose) {
        AsyncServicePool<Service> asyncPool = new AsyncServicePool<Service>(pool, shutdownPoolOnClose, _mockExecutor, true);
        _asyncServicePools.add(asyncPool);
        return asyncPool;
    }

    private static interface Service {
    }
}