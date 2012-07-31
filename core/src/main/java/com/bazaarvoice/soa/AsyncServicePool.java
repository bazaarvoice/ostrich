package com.bazaarvoice.soa;

import java.io.Closeable;
import java.util.Collection;
import java.util.concurrent.Future;

/**
 * An asynchronous service pool.  This mimics the behavior of a {@link ServicePool}, but instead of executing its
 * callbacks synchronously, it will run them in the background.
 *
 * @param <S> The service interface that this pool keeps track of end points for.
 */
public interface AsyncServicePool<S> extends Closeable {
    /**
     * Execute a request asynchronously against one of the remote services in this {@code ServicePool}, returning
     * a future representing the asynchronous call.
     *
     * @param retryPolicy The retry policy for the operation.
     * @param callback    The user provided callback to invoke.
     * @param <R>         The return type for the call.
     */
    <R> Future<R> execute(RetryPolicy retryPolicy, ServiceCallback<S, R> callback);

    /**
     * Execute a request asynchronously against <b>ALL</b> of the remote services in this {@code ServicePool},
     * returning a future for each asynchronous call.
     * <p/>
     * NOTE: It is undefined how the implementation handles the situation where an end point is discovered or removed
     * while the {@code executeOnAll} operation is executing.
     *
     * @param retryPolicy The retry policy for each operation.
     * @param callback    The user provided callback to invoke.
     * @param <R>         The return type for the call.
     */
    <R> Collection<Future<R>> executeOnAll(RetryPolicy retryPolicy, ServiceCallback<S, R> callback);

    /**
     * Execute a request asynchronously against some of the remote services in this {@code ServicePool},
     * returning a future for each asynchronous call.
     * <p/>
     * NOTE: It is undefined how the implementation handles the situation where an end point is discovered or removed
     * while the {@code executeOn} operation is executing.
     *
     * @param predicate   A predicate indicating which service end points the request should be executed against.  If
     *                    the predicate returns {@code true} then the request will be executed against that end point.
     * @param retryPolicy The retry policy for each operation.
     * @param callback    The user provided callback to invoke.
     * @param <R>         The return type for the call.
     */
    <R> Collection<Future<R>> executeOn(ServiceEndPointPredicate predicate, RetryPolicy retryPolicy,
                                        ServiceCallback<S, R> callback);
}
