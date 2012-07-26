package com.bazaarvoice.soa.loadbalance;

import com.bazaarvoice.soa.LoadBalanceAlgorithm;
import com.bazaarvoice.soa.ServiceEndPoint;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

import java.util.Iterator;
import java.util.List;
import java.util.Random;

public class RandomAlgorithm implements LoadBalanceAlgorithm {
    private final Random _rnd = new Random();

    @Override
    public ServiceEndPoint choose(Iterable<ServiceEndPoint> endPoints) {
        Preconditions.checkNotNull(endPoints);

        Iterator<ServiceEndPoint> iter = endPoints.iterator();
        if (!iter.hasNext()) {
            return null;
        }

        List<ServiceEndPoint> list = Lists.newArrayList(iter);
        return list.get(_rnd.nextInt(list.size()));
    }
}
