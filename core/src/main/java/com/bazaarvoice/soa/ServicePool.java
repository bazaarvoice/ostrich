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
     * @return The result provided by the callback.
     */
    <R> R execute(RetryPolicy retryPolicy, ServiceCallback<S, R> callback);

    /**
     * Execute a request synchronously against one of the remote services in this <code>ServicePool</code> using
     * the specified partition information to narrow down the suitable service end points.
     *
     * @param partitionContext The partition context.
     * @param retryPolicy The retry policy for the operation.
     * @param callback The user provided callback to invoke with a service end point.
     * @param <R> The return type for the call.
     * @return The result provided by the callback.
     */
    <R> R execute(PartitionContext partitionContext, RetryPolicy retryPolicy, ServiceCallback<S, R> callback);

    /**
     * Attempts to find a healthy end point. Performs health checks until a healthy end point is found, all available
     * end points are exhausted, or execution of a health check throws an exception that is deemed not retriable.
     *
     * @return {@code HealthCheckResults} containing the first healthy result found (if any), and all unhealthy results
     * encountered before a healthy one. If there are no end points in the pool, the {@code HealthCheckResults} will
     * contain no results.
     */
    HealthCheckResults checkForHealthyEndPoint();

    /**
     * Return the number of valid end points that this service pool knows about.  This will include end points that have
     * never thrown exceptions during execution (even those that have never been interacted with) and end points that
     * were previously known as bad but have since had a successful health check.  If this method returns non-zero, then
     * a call to execute should not fail with an {@link com.bazaarvoice.soa.exceptions.OnlyBadHostsException} or
     * {@link com.bazaarvoice.soa.exceptions.NoAvailableHostsException} exception.
     */
    int getNumValidEndPoints();

    /**
     * Return the number of end points that this service pool considers to be in a bad state.  A bad end point is one
     * that a previous operation was attempted on and the attempt failed.  An end point could be considered bad prior to
     * its health check being called.
     * <p/>
     * This combined with {@link #getNumValidEndPoints()} gives visibility into the total number of end points that the
     * {@code ServicePool} knows about.
     */
    int getNumBadEndPoints();
}
