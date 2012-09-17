package com.bazaarvoice.soa.partition;

import com.bazaarvoice.soa.PartitionContext;
import com.bazaarvoice.soa.ServiceEndPoint;
import com.bazaarvoice.soa.ServicePool;

/**
 * Filters a set of end points based on a {@link PartitionContext} object.
 */
public interface PartitionFilter {
    /**
     * Filters a set of end points based on a {@link PartitionContext} object.
     *
     * @param endPoints A collection of end points.  Known (or suspected) bad end points have been removed.
     * @param partitionContext The {@link com.bazaarvoice.soa.PartitionContext} object passed to the
     *                         {@link ServicePool#execute} method.
     * @return A collection of end points that may service the specified partition.  This might be the same object
     *         passed in the {@code endPoints} argument if all end points may service the specified partition.
     */
    Iterable<ServiceEndPoint> filter(Iterable<ServiceEndPoint> endPoints, PartitionContext partitionContext);
}
