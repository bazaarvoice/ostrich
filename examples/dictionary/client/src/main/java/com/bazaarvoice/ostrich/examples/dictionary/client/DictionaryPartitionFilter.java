package com.bazaarvoice.ostrich.examples.dictionary.client;

import com.bazaarvoice.ostrich.PartitionContext;
import com.bazaarvoice.ostrich.ServiceEndPoint;
import com.bazaarvoice.ostrich.partition.PartitionFilter;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;

import static com.google.common.base.Preconditions.checkNotNull;

public class DictionaryPartitionFilter implements PartitionFilter {
    @Override
    public Iterable<ServiceEndPoint> filter(Iterable<ServiceEndPoint> endPoints, PartitionContext partitionContext) {
        final String word = (String) checkNotNull(partitionContext.get());

        return Iterables.filter(endPoints, new Predicate<ServiceEndPoint>() {
            @Override
            public boolean apply(ServiceEndPoint endPoint) {
                return Payload.valueOf(endPoint.getPayload()).getPartition().apply(word);
            }
        });
    }
}
