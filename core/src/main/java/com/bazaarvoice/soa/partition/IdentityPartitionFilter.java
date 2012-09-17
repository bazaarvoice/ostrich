package com.bazaarvoice.soa.partition;

import com.bazaarvoice.soa.PartitionContext;
import com.bazaarvoice.soa.ServiceEndPoint;

/**
 * A partition filter with a {@link #filter} method that always returns the same end points it is given.
 */
public class IdentityPartitionFilter implements PartitionFilter {
    @Override
    public Iterable<ServiceEndPoint> filter(Iterable<ServiceEndPoint> endPoints, PartitionContext partitionContext) {
        return endPoints;
    }
}
