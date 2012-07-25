package com.bazaarvoice.soa;

public interface ServiceFactory<S> {
    // TODO: getServiceName and getLoadBalanceAlgorithm don't feel right here.
    String getServiceName();
    LoadBalanceAlgorithm getLoadBalanceAlgorithm(ServicePoolStatistics stats);

    S create(ServiceEndPoint endpoint);
    boolean isHealthy(ServiceEndPoint endpoint);
}