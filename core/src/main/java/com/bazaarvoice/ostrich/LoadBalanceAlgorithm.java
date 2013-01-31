package com.bazaarvoice.ostrich;

public interface LoadBalanceAlgorithm {
    /**
     * Selects an end point to use based on a load balancing algorithm.  If no end point can be chosen, then
     * <code>null</code> is returned.
     *
     * @param endPoints The end points to choose from.
     * @param statistics Usage statistics about the end points in case the load balancing algorithm needs some
     *                   knowledge of the service pool's state.
     * @return Which end point to use or <code>null</code> if one couldn't be chosen.
     */
    ServiceEndPoint choose(Iterable<ServiceEndPoint> endPoints, ServicePoolStatistics statistics);
}
