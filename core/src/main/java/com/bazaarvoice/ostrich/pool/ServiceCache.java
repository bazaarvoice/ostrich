package com.bazaarvoice.ostrich.pool;

import com.bazaarvoice.ostrich.ServiceEndPoint;

import java.io.Closeable;

/**
 * A cache for service instances. Useful if there's more than insignificant
 * overhead in creating service connections
 *
 * @param <S> the type parameter of Service
 */
public interface ServiceCache<S> extends Closeable {

    /**
     * Retrieves a cached service instance for a registered end point. A new service handle is
     * created and returned for a new end point. Once the checked out instance is no longer in
     * use, it should be returned by calling {@link #checkIn}. It is recommended that end
     * points should be registered with the cache with {@link #register} first, however
     * {@code #checkOut} can internally register a new end point if it's missed due to inherent
     * race condition between {@code #checkOut} and {@link #register}
     *
     * @param endPoint The end point to retrieve the instance of service handle for
     * @return A service handle that contains a cached service instance for the requested end point
     * @throws Exception if internal error prohibits from creating/returning a handle
     */
    ServiceHandle<S> checkOut(ServiceEndPoint endPoint) throws Exception;

    /**
     * Returns a service instance for an end point to the cache so that it may be used by
     * others
     *
     * @param handle The service handle that is being checked in
     * @throws Exception the exception
     */
    void checkIn(ServiceHandle<S> handle) throws Exception;

    /**
     * Gets num idle instances.
     *
     * @param endPoint to find idle instance count for
     * @return number of registered service handles for the given endPoint
     */
    int getNumIdleInstances(ServiceEndPoint endPoint);

    /**
     * Gets num active instances.
     *
     * @param endPoint to find active instance count for
     * @return number of active service handles for a given endPoint
     */
    int getNumActiveInstances(ServiceEndPoint endPoint);

    /**
     * Closes the cache
     */
    @Override
    void close();

    /**
     * As new end points gets added to the service pool, as part of initialization
     * this method allows the pool to register that endpoint to the cache. Ideally
     * this allows the cache to have a service handle ready for checkOut before
     * the first request comes in.
     *
     * This is particularly helpful for heavyweight clients that are expensive to
     * create, thus allows to pre-load the cache with available endPoints eagerly
     * instead of lazy loading during {@link #checkOut}
     *
     * @param endPoint to register with the cache
     */
    void register(ServiceEndPoint endPoint);

    /**
     * Evicts an endPoint from the cache
     *
     * @param endPoint to evict from the cache
     */
    void evict(ServiceEndPoint endPoint);
}
