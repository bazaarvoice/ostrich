package com.bazaarvoice.soa.pool;

import com.bazaarvoice.soa.PartitionContext;
import com.bazaarvoice.soa.RetryPolicy;
import com.bazaarvoice.soa.ServiceCallback;
import com.bazaarvoice.soa.ServiceEndPoint;
import com.bazaarvoice.soa.ServiceEndPointPredicate;
import com.bazaarvoice.soa.exceptions.MaxRetriesException;
import com.bazaarvoice.soa.exceptions.ServiceException;
import com.google.common.base.Ticker;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.io.Closeables;
import com.google.common.util.concurrent.MoreExecutors;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class AsyncServicePoolTest {
    private static final RetryPolicy NEVER_RETRY = mock(RetryPolicy.class);

    @SuppressWarnings("unchecked")
    private final ServicePool<Service> _mockPool = mock(ServicePool.class);

    private final Ticker _mockTicker = mock(Ticker.class);
    private final ExecutorService _mockExecutor = mock(ExecutorService.class);
    private final Collection<AsyncServicePool<Service>> _asyncServicePools = Lists.newArrayList();

    @Before
    public void setup() {
        when(_mockPool.getServiceName()).thenReturn(Service.class.getSimpleName());
    }

    @After
    public void teardown() {
        for (AsyncServicePool<Service> pool : _asyncServicePools) {
            Closeables.closeQuietly(pool);
        }
    }

    @Test(expected =  NullPointerException.class)
    public void testNullTicker() {
        new AsyncServicePool<Service>(null, _mockPool, true, _mockExecutor, true);
    }

    @Test(expected = NullPointerException.class)
    public void testNullServicePool() {
        new AsyncServicePool<Service>(_mockTicker, null, true, _mockExecutor, true);
    }

    @SuppressWarnings("unchecked")
    @Test(expected = NullPointerException.class)
    public void testNullExecutorService() {
        new AsyncServicePool<Service>(_mockTicker, _mockPool, true, null, true);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testSubmitsCallableToExecutor() {
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

    @SuppressWarnings("unchecked")
    @Test
    public void testExecutesPartitionContextInPool() {
        AsyncServicePool<Service> pool = newAsyncPool(MoreExecutors.sameThreadExecutor());

        ServiceCallback<Service, Void> callback = (ServiceCallback<Service, Void>) mock(ServiceCallback.class);
        PartitionContext context = mock(PartitionContext.class);
        pool.execute(context, NEVER_RETRY, callback);

        verify(_mockPool).execute(same(context), same(NEVER_RETRY), same(callback));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testExecuteAllSubmitsMultipleCallablesToExecutor() {
        List<ServiceEndPoint> endPoints = Lists.newArrayList(
                mock(ServiceEndPoint.class),
                mock(ServiceEndPoint.class),
                mock(ServiceEndPoint.class)
        );
        when(_mockPool.getAllEndPoints()).thenReturn(endPoints);

        AsyncServicePool<Service> pool = newAsyncPool();
        pool.executeOnAll(NEVER_RETRY, mock(ServiceCallback.class));

        verify(_mockExecutor, times(endPoints.size())).submit(any(Callable.class));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testExecuteAllExecutesCallbacksInPool() throws Exception {
        ServiceEndPoint FOO = mock(ServiceEndPoint.class);
        ServiceEndPoint BAR = mock(ServiceEndPoint.class);
        ServiceEndPoint BAZ = mock(ServiceEndPoint.class);
        when(_mockPool.getAllEndPoints()).thenReturn(Lists.newArrayList(FOO, BAR, BAZ));

        // Use a real executor so that it can actually call into the callback
        AsyncServicePool<Service> pool = newAsyncPool(MoreExecutors.sameThreadExecutor());

        ServiceCallback<Service, Void> callback = (ServiceCallback<Service, Void>) mock(ServiceCallback.class);
        pool.executeOnAll(NEVER_RETRY, callback);

        verify(_mockPool).executeOnEndPoint(same(FOO), same(callback));
        verify(_mockPool).executeOnEndPoint(same(BAR), same(callback));
        verify(_mockPool).executeOnEndPoint(same(BAZ), same(callback));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testExecuteAllReturnsValueInFuture() throws Exception {
        ServiceEndPoint FOO = mock(ServiceEndPoint.class);
        ServiceEndPoint BAR = mock(ServiceEndPoint.class);
        ServiceEndPoint BAZ = mock(ServiceEndPoint.class);
        when(_mockPool.getAllEndPoints()).thenReturn(Lists.newArrayList(FOO, BAR, BAZ));

        when(_mockPool.executeOnEndPoint(same(FOO), any(ServiceCallback.class))).thenReturn("FOO");
        when(_mockPool.executeOnEndPoint(same(BAR), any(ServiceCallback.class))).thenReturn("BAR");
        when(_mockPool.executeOnEndPoint(same(BAZ), any(ServiceCallback.class))).thenReturn("BAZ");

        // Use a real executor so that it can actually call into the callback
        AsyncServicePool<Service> pool = newAsyncPool(MoreExecutors.sameThreadExecutor());

        Collection<Future<String>> futures = pool.executeOnAll(NEVER_RETRY, mock(ServiceCallback.class));
        assertEquals(3, futures.size());

        Set<String> results = Sets.newHashSet();
        for (Future<String> future : futures) {
            results.add(future.get());
        }
        assertEquals(Sets.newHashSet("FOO", "BAR", "BAZ"), results);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testExecuteAllWrapsNonRetriableExceptionInFuture() throws Exception {
        RuntimeException exception = mock(RuntimeException.class);
        when(_mockPool.getAllEndPoints()).thenReturn(Lists.newArrayList(mock(ServiceEndPoint.class)));
        when(_mockPool.executeOnEndPoint(any(ServiceEndPoint.class), any(ServiceCallback.class))).thenThrow(exception);
        when(_mockPool.isRetriableException(any(Exception.class))).thenReturn(false);

        // Use a real executor so that it can actually call into the callback
        AsyncServicePool<Service> pool = newAsyncPool(MoreExecutors.sameThreadExecutor());

        Collection<Future<Void>> futures = pool.executeOnAll(NEVER_RETRY, mock(ServiceCallback.class));
        assertEquals(1, futures.size());

        Future<Void> future = futures.iterator().next();
        try {
            future.get(10, TimeUnit.SECONDS);
            fail();
        } catch(ExecutionException e) {
            assertSame(exception, e.getCause());
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testExecuteAllPropagatesMaxRetriesExceptionWhenOutOfRetries() throws Exception {
        ServiceException exception = mock(ServiceException.class);
        when(_mockPool.getAllEndPoints()).thenReturn(Lists.newArrayList(mock(ServiceEndPoint.class)));
        when(_mockPool.executeOnEndPoint(any(ServiceEndPoint.class), any(ServiceCallback.class))).thenThrow(exception);
        when(_mockPool.isRetriableException(any(Exception.class))).thenReturn(true);

        // Use a real executor so that it can actually call into the callback
        AsyncServicePool<Service> pool = newAsyncPool(MoreExecutors.sameThreadExecutor());

        Collection<Future<Void>> futures = pool.executeOnAll(NEVER_RETRY, mock(ServiceCallback.class));
        assertEquals(1, futures.size());

        Future<Void> future = futures.iterator().next();
        try {
            future.get(10, TimeUnit.SECONDS);
            fail();
        } catch(ExecutionException e) {
            assertTrue(e.getCause() instanceof MaxRetriesException);
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testExecuteOnSubmitsValidCallablesToExecutor() {
        ServiceEndPoint FOO = mock(ServiceEndPoint.class);
        ServiceEndPoint BAR = mock(ServiceEndPoint.class);
        ServiceEndPoint BAZ = mock(ServiceEndPoint.class);
        when(_mockPool.getAllEndPoints()).thenReturn(Lists.newArrayList(FOO, BAR, BAZ));

        ServiceEndPointPredicate predicate = mock(ServiceEndPointPredicate.class);
        when(predicate.apply(same(FOO))).thenReturn(false);
        when(predicate.apply(same(BAR))).thenReturn(true);
        when(predicate.apply(same(BAZ))).thenReturn(true);

        AsyncServicePool<Service> pool = newAsyncPool();
        pool.executeOn(predicate, NEVER_RETRY, mock(ServiceCallback.class));

        verify(_mockExecutor, times(2)).submit(any(Callable.class));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testExecuteOnSubmitsNoCallablesToExecutor() {
        ServiceEndPoint FOO = mock(ServiceEndPoint.class);
        ServiceEndPoint BAR = mock(ServiceEndPoint.class);
        ServiceEndPoint BAZ = mock(ServiceEndPoint.class);
        when(_mockPool.getAllEndPoints()).thenReturn(Lists.newArrayList(FOO, BAR, BAZ));

        ServiceEndPointPredicate predicate = mock(ServiceEndPointPredicate.class);
        when(predicate.apply(same(FOO))).thenReturn(false);
        when(predicate.apply(same(BAR))).thenReturn(false);
        when(predicate.apply(same(BAZ))).thenReturn(false);

        AsyncServicePool<Service> pool = newAsyncPool();
        pool.executeOn(predicate, NEVER_RETRY, mock(ServiceCallback.class));

        verify(_mockExecutor, never()).submit(any(Callable.class));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testExecuteOnExecutesValidCallbacksInPool() throws Exception {
        ServiceEndPoint FOO = mock(ServiceEndPoint.class);
        ServiceEndPoint BAR = mock(ServiceEndPoint.class);
        ServiceEndPoint BAZ = mock(ServiceEndPoint.class);
        when(_mockPool.getAllEndPoints()).thenReturn(Lists.newArrayList(FOO, BAR, BAZ));

        ServiceEndPointPredicate predicate = mock(ServiceEndPointPredicate.class);
        when(predicate.apply(same(FOO))).thenReturn(false);
        when(predicate.apply(same(BAR))).thenReturn(true);
        when(predicate.apply(same(BAZ))).thenReturn(true);

        // Use a real executor so that it can actually call into the callback
        AsyncServicePool<Service> pool = newAsyncPool(MoreExecutors.sameThreadExecutor());

        ServiceCallback<Service, Void> callback = (ServiceCallback<Service, Void>) mock(ServiceCallback.class);
        pool.executeOn(predicate, NEVER_RETRY, callback);

        verify(_mockPool, never()).executeOnEndPoint(same(FOO), same(callback));
        verify(_mockPool).executeOnEndPoint(same(BAR), same(callback));
        verify(_mockPool).executeOnEndPoint(same(BAZ), same(callback));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testExecuteOnExecutesNoCallbacksInPool() throws Exception {
        ServiceEndPoint FOO = mock(ServiceEndPoint.class);
        ServiceEndPoint BAR = mock(ServiceEndPoint.class);
        ServiceEndPoint BAZ = mock(ServiceEndPoint.class);
        when(_mockPool.getAllEndPoints()).thenReturn(Lists.newArrayList(FOO, BAR, BAZ));

        ServiceEndPointPredicate predicate = mock(ServiceEndPointPredicate.class);
        when(predicate.apply(same(FOO))).thenReturn(false);
        when(predicate.apply(same(BAR))).thenReturn(false);
        when(predicate.apply(same(BAZ))).thenReturn(false);

        // Use a real executor so that it can actually call into the callback
        AsyncServicePool<Service> pool = newAsyncPool(MoreExecutors.sameThreadExecutor());
        pool.executeOn(predicate, NEVER_RETRY, mock(ServiceCallback.class));

        verify(_mockPool, never()).executeOnEndPoint(any(ServiceEndPoint.class), any(ServiceCallback.class));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testExecuteOnCanFilterEndPoints() throws Exception {
        ServiceEndPoint FOO = mock(ServiceEndPoint.class);
        ServiceEndPoint BAR = mock(ServiceEndPoint.class);
        ServiceEndPoint BAZ = mock(ServiceEndPoint.class);
        when(_mockPool.getAllEndPoints()).thenReturn(Lists.newArrayList(FOO, BAR, BAZ));

        when(_mockPool.executeOnEndPoint(same(FOO), any(ServiceCallback.class))).thenReturn("FOO");
        when(_mockPool.executeOnEndPoint(same(BAR), any(ServiceCallback.class))).thenReturn("BAR");
        when(_mockPool.executeOnEndPoint(same(BAZ), any(ServiceCallback.class))).thenReturn("BAZ");

        // Use a real executor so that it can actually call into the callback
        AsyncServicePool<Service> pool = newAsyncPool(MoreExecutors.sameThreadExecutor());

        ServiceEndPointPredicate predicate = mock(ServiceEndPointPredicate.class);
        when(predicate.apply(same(FOO))).thenReturn(false);
        when(predicate.apply(same(BAR))).thenReturn(true);
        when(predicate.apply(same(BAZ))).thenReturn(false);

        Collection<Future<String>> futures = pool.executeOn(predicate, NEVER_RETRY, mock(ServiceCallback.class));
        assertEquals(1, futures.size());
        assertEquals("BAR", futures.iterator().next().get());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testExecuteOnChecksRetriability() throws Exception {
        RuntimeException exception = mock(RuntimeException.class);
        when(_mockPool.getAllEndPoints()).thenReturn(Lists.newArrayList(mock(ServiceEndPoint.class)));
        when(_mockPool.executeOnEndPoint(any(ServiceEndPoint.class), any(ServiceCallback.class))).thenThrow(exception);

        ServiceEndPointPredicate predicate = mock(ServiceEndPointPredicate.class);
        when(predicate.apply(any(ServiceEndPoint.class))).thenReturn(true);

        // Use a real executor so that it can actually call into the callback
        AsyncServicePool<Service> pool = newAsyncPool(MoreExecutors.sameThreadExecutor());

        pool.executeOn(predicate, NEVER_RETRY, mock(ServiceCallback.class));

        verify(_mockPool).isRetriableException(exception);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testRetriableExceptionIsRetried() throws Exception {
        when(_mockPool.getAllEndPoints()).thenReturn(Lists.newArrayList(mock(ServiceEndPoint.class)));

        // Set things up to throw and exception on first run.
        when(_mockPool.executeOnEndPoint(any(ServiceEndPoint.class), any(ServiceCallback.class)))
                .thenThrow(RuntimeException.class).thenReturn(null);

        // Allow retry.
        when(_mockPool.isRetriableException(any(Exception.class))).thenReturn(true);

        // Use a real executor so that it can actually call into the callback
        AsyncServicePool<Service> pool = newAsyncPool(MoreExecutors.sameThreadExecutor());

        RetryPolicy retry = mock(RetryPolicy.class);
        when(retry.allowRetry(anyInt(), anyLong())).thenReturn(true);

        Collection<Future<Void>> futures = pool.executeOnAll(retry, mock(ServiceCallback.class));
        assertEquals(1, futures.size());

        Future<Void> future = futures.iterator().next();
        future.get(10, TimeUnit.SECONDS);
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
        AsyncServicePool<Service> pool =
                new AsyncServicePool<Service>(_mockTicker, _mockPool, true, executor, shutdownExecutorOnClose);
        _asyncServicePools.add(pool);
        return pool;
    }

    private AsyncServicePool<Service> newAsyncPool(ServicePool<Service> pool, boolean shutdownPoolOnClose) {
        AsyncServicePool<Service> asyncPool =
                new AsyncServicePool<Service>(_mockTicker, pool, shutdownPoolOnClose, _mockExecutor, true);
        _asyncServicePools.add(asyncPool);
        return asyncPool;
    }

    private static interface Service {
    }
}