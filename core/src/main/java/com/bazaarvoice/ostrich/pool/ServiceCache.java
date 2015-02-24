package com.bazaarvoice.ostrich.pool;

import com.bazaarvoice.ostrich.ServiceEndPoint;

import java.io.Closeable;

public interface ServiceCache<S> extends Closeable {

    /**
     * Check out the instance of service handle.
     *
     * @param endPoint The end point to retrieve the instance of service handle
     * @return the service handle
     */
    public ServiceHandle<S> checkOut(ServiceEndPoint endPoint) throws Exception;

    /**
     * Mimics the behavior of a cache check in,
     *
     * @param handle The service handle that is being checked in
     */
    public void checkIn(ServiceHandle<S> handle) throws Exception;

    /**
     * @param endPoint to find idle instance count
     * @return number of registered service handles for the given endpoint
     */
    public int getNumIdleInstances(ServiceEndPoint endPoint);

    /**
     * @param endPoint to find active instance count
     * @return number of active service handles for a given endpoint
     */
    public int getNumActiveInstances(ServiceEndPoint endPoint);

    /**
     * closes the cache
     */
    @Override
    public void close();

    /**
     * @param endPoint to register on the cache
     */
    public void register(ServiceEndPoint endPoint);

    /**
     * @param endPoint to evict from the cache
     */
    public void evict(ServiceEndPoint endPoint);

}
