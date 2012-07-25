package com.bazaarvoice.soa;

public interface ServicePoolStatistics {
    int getNumIdleCachedInstances(ServiceEndPoint endPoint);

    int getNumActiveInstances(ServiceEndPoint endPoint);
}
