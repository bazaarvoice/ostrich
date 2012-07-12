package com.bazaarvoice.soa;

import com.google.common.base.Predicate;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.Closeable;
import java.util.List;

/**
 * A <code>ServicePool</code> keeps track of service endpoints for a particular service.  Internally it
 * understands how requests should be load balanced across endpoints and takes a service owner's
 * <code>LoadBalanceAlgorithm</code> into account when deciding which endpoint to use.  In addition the
 * <code>ServicePool</code> is also able to monitor the health of a service endpoint if the service owner provides a
 * health check implementation.  It is able to use the health check information as a guide in selecting service
 * endpoints in order to avoid providing an unhealthy service endpoint to a user.
 * <p/>
 * The <code>ServicePool</code> provides an automatically managed resource pool model to consumers.  A consumer
 * provides a callback to the <code>ServicePool</code>  to execute a piece of code against a service endpoint.  The
 * <code>ServicePool</code> will then select a suitable service endpoint to use and then invoke the user's callback
 * with a handle to the endpoint.  At that point the user can interact with the remote endpoint however it wants,
 * calling any APIs necessary.  When the callback returns, the connection with the remote service is cleaned up.  If
 * during the execution of the callback a service related error occurs, the service endpoint will be marked as
 * unhealthy, and the operation retried, as allowed by the <code>RetryPolicy</code> the user specifies.
 * <p/>
 * For example, assume that we have a mythical <code>CalculatorService</code> with <code>add</code>, <code>sub</code>,
 * etc. methods on it.  Usage of a <code>ServicePool</code> for that service might look like:
 * <pre>
 * int sum = calculatorPool.execute(new RetryNTimes(3), new ServiceCallback<CalculatorService, Integer>() {
 *     public Integer call(CalculatorService calculator) {
 *         return calculator.add(1, calculator.sub(3, 2));
 *     }
 * });
 * </pre>
 *
 * @param <S> The service interface that this pool keeps track of endpoints for.
 */
public interface ServicePool<S> extends Closeable {
    /**
     * Execute a request synchronously against one of the remote services in this <code>ServicePool</code>.
     *
     * @param retryPolicy The retry policy for the operation.
     * @param callback The user provided callback to invoke with a service endpoint.
     * @param <R> The return type for the call.
     */
    <R> R execute(RetryPolicy retryPolicy, ServiceCallback<S, R> callback);

    /**
     * Execute a request asynchronously against one of the remote services in this <code>ServicePool</code>.  A
     * <code>ListenableFuture</code> is returned that will eventually contain the result of the operation.
     *
     * @param retryPolicy The retry policy for the operation.
     * @param callback The user provided callback to invoke with a service endpoint.
     * @param <R> The return type for the call.
     */
    <R> ListenableFuture<R> executeAsync(RetryPolicy retryPolicy, ServiceCallback<S, R> callback);

    /**
     * Execute a request synchronously against a subset of the remote services.
     *
     * @param retryPolicy The retry policy for the operation.
     * @param filter The filter to check before calling the service
     * @param callback The user provided callback to invoke with a service endpoint.
     * @param <R> The return type for the call.
     */
    <R> List<R> executeOnSome(RetryPolicy retryPolicy, Predicate<ServiceEndPoint> filter, ServiceCallback<S, R> callback);

    /**
     * Execute a request asynchronously against a subset of the remote services. A list of
     * <code>ListenableFuture</code>s is returned that will eventually contain the result of the operation.
     *
     * @param retryPolicy The retry policy for the operation.
     * @param filter The filter to check before calling the service
     * @param callback The user provided callback to invoke with a service endpoint.
     * @param <R> The return type for the call.
     */
    <R> List<ListenableFuture<R>> executeAsyncOnSome(RetryPolicy retryPolicy, Predicate<ServiceEndPoint> filter, ServiceCallback<S, R> callback);

    /**
     * Execute a request synchronously against all of the remote services.
     *
     * @param retryPolicy The retry policy for the operation.
     * @param callback The user provided callback to invoke with a service endpoint.
     * @param <R> The return type for the call.
     */
    <R> List<R> executeOnAll(RetryPolicy retryPolicy, ServiceCallback<S, R> callback);

    /**
     * Execute a request asynchronously against all of the remote services. A list of
     * <code>ListenableFuture</code>s is returned that will eventually contain the result of the operation.
     *
     * @param retryPolicy The retry policy for the operation.
     * @param callback The user provided callback to invoke with a service endpoint.
     * @param <R> The return type for the call.
     */
    <R> List<ListenableFuture<R>> executeAsyncOnAll(RetryPolicy retryPolicy, ServiceCallback<S, R> callback);

    /**
     * Returns a dynamic proxy that implements the service interface and implicitly wraps every call to a service
     * method with a call to the {@link #execute} method.  This is appropriate for stateless services where it's
     * sensible for the same retry policy to apply to every method.
     * <p>
     * In contrast to proxies created with {@link com.bazaarvoice.soa.pool.ServicePoolBuilder#buildProxy(RetryPolicy)},
     * proxies returned by this method do not provide a {@code close} method that closes the service pool.
     * <p>
     * Implementation restriction: dynamic proxies are only supported when the service interface {@code S} is an
     * interface.  They're not supported when {@code S} is a concrete class.
     *
     * @param retryPolicy The retry policy for every operation.
     * @return The dynamic proxy instance.
     */
    S newProxy(RetryPolicy retryPolicy);
}
