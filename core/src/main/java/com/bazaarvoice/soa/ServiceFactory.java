package com.bazaarvoice.soa;

/**
 * A factory for service instances. Provides instances to be used in {@link ServiceCallback}s as well as
 * general information about the service.
 * @param <S> The type of the service.
 */
public interface ServiceFactory<S> {
    // TODO: getServiceName and getLoadBalanceAlgorithm don't feel right here.

    /**
     * Get the name of the service this factory provides.
     * @return The name of the service.
     */
    String getServiceName();

    /**
     * Get the {@code LoadBalanceAlgorithm} that should be used for this service. {@code ServicePoolStatistics} are
     * provided in case the load balancer needs to have some knowledge of the service pool's state.
     * @param stats A live view of information about the service pool.
     * @return A load balance algorithm to choose between available end points for the service.
     */
    LoadBalanceAlgorithm getLoadBalanceAlgorithm(ServicePoolStatistics stats);

    /**
     * Create a service instance for a given end point.
     * @param endPoint The end point the created instance should connect to.
     * @return A new service instance.
     */
    S create(ServiceEndPoint endPoint);

    /**
     * Perform a health check on an end point. Typically done if a call to a service instance associated with the end
     * point fails, and the health of the end point needs to be ensured before making it available for use again.
     * @param endPoint The end point the check the health of.
     * @return {@code true} if the health check succeeded, {@code false} otherwise.
     */
    boolean isHealthy(ServiceEndPoint endPoint);

    /**
     * Determine whether or not an exception should lead to a retry if the {@link RetryPolicy} allows. The source of the
     * exception can be from the {link #create} method, a call to a {@link ServiceCallback}, or occasionally due to an
     * issue with service caching in a {@link ServicePool}. The first two are under the purview of the
     * service provider. Exceptions originating from a {@code ServicePool} will be from package
     * {@link com.bazaarvoice.soa.exceptions}. If the exception is relevant to a specific {@link ServiceEndPoint}, that
     * end point will be marked as bad in the pool before retrying if the exception is deemed retriable.
     *
     * @param exception An exception encountered during an attempt to execute a service callback or retrieve a service
     * instance to execute against.
     * @return {@code true}, if the exception should lead to a retry (likely on a different end point).
     */
    boolean isRetriableException(Exception exception);
}