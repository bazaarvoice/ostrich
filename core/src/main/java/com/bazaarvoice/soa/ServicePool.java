package com.bazaarvoice.soa;

import java.io.Closeable;

/**
 * A <code>ServicePool</code> keeps track of service end points for a particular service.  Internally it
 * understands how requests should be load balanced across end points and takes a service owner's
 * <code>LoadBalanceAlgorithm</code> into account when deciding which end point to use.  In addition the
 * <code>ServicePool</code> is also able to monitor the health of a service end point if the service owner provides a
 * health check implementation.  It is able to use the health check information as a guide in selecting service
 * end points in order to avoid providing an unhealthy service end point to a user.
 * <p/>
 * The <code>ServicePool</code> provides an automatically managed resource pool model to consumers.  A consumer
 * provides a callback to the <code>ServicePool</code>  to execute a piece of code against a service end point.  The
 * <code>ServicePool</code> will then select a suitable service end point to use and then invoke the user's callback
 * with a handle to the end point.  At that point the user can interact with the remote end point however it wants,
 * calling any APIs necessary.  When the callback returns, the connection with the remote service is cleaned up.  If
 * during the execution of the callback a service related error occurs, the service end point will be marked as
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
 * @param <S> The service interface that this pool keeps track of end points for.
 */
public interface ServicePool<S> extends Closeable {
    /**
     * Execute a request synchronously against one of the remote services in this <code>ServicePool</code>.
     *
     * @param retryPolicy The retry policy for the operation.
     * @param callback The user provided callback to invoke with a service end point.
     * @param <R> The return type for the call.
     */
    <R> R execute(RetryPolicy retryPolicy, ServiceCallback<S, R> callback);
}
