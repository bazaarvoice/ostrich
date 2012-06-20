package com.bazaarvoice.soa;

import java.util.concurrent.Future;

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
public interface ServicePool<S> {
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
    <R> Future<R> executeAsync(RetryPolicy retryPolicy, ServiceCallback<S, R> callback);
}