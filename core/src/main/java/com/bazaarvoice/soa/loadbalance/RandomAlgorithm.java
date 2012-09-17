package com.bazaarvoice.soa.loadbalance;

import com.bazaarvoice.soa.LoadBalanceAlgorithm;
import com.bazaarvoice.soa.ServiceEndPoint;
import com.bazaarvoice.soa.ServicePoolStatistics;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

import java.util.Iterator;
import java.util.List;
import java.util.Random;

public class RandomAlgorithm implements LoadBalanceAlgorithm {
    private final Random _rnd = new Random();

    @Override
    public ServiceEndPoint choose(Iterable<ServiceEndPoint> endPoints, ServicePoolStatistics statistics) {
        Preconditions.checkNotNull(endPoints);

        Iterator<ServiceEndPoint> iter = endPoints.iterator();
        if (!iter.hasNext()) {
            return null;
        }

        List<ServiceEndPoint> list = Lists.newArrayList(iter);
        if (list.size() == 1) {
            return list.get(0);
        }
        return list.get(_rnd.nextInt(list.size()));
    }
}
