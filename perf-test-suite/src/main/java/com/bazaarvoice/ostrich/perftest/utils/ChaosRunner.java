package com.bazaarvoice.ostrich.perftest.utils;

import com.bazaarvoice.ostrich.ServiceEndPoint;
import com.bazaarvoice.ostrich.metrics.Metrics;
import com.bazaarvoice.ostrich.perftest.core.Service;
import com.bazaarvoice.ostrich.pool.ServiceCache;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.google.common.collect.ImmutableList;

import java.util.List;

public class ChaosRunner {

    private final ServiceCache<Service<String, String>> _serviceCache;
    private final int _chaosWorkers;
    private final Meter _chaosMeter, _stableMeter;
    private final int _chaosInterval;

    public ChaosRunner(ServiceCache<Service<String, String>> serviceCache, Arguments arguments, MetricRegistry metricRegistry) {
        _serviceCache = serviceCache;
        _chaosWorkers = arguments.getChaosWorkers();
        Metrics.InstanceMetrics _metrics = Metrics.forInstance(metricRegistry, this, "ChaosRunner");
        _chaosMeter = _metrics.meter("Chaos");
        _stableMeter = _metrics.meter("Stable");
        _chaosInterval = arguments.getChaosInterval();
    }

    public List<Thread> generateChaosWorkers() {
        ImmutableList.Builder<Thread> chaosWorkersBuilder = ImmutableList.builder();
        for(int i=0; i<_chaosWorkers; i++) {
            Runnable runnable = new Runnable() {
                @Override
                public void run() {
                    while(!Thread.interrupted()) {
                        try {
                            int sleepTime = Utilities.getRandomInt(_chaosInterval);
                            Utilities.sleepForSeconds(sleepTime);
                            String hashName = HashFunction.getRandomHashName();
                            ServiceEndPoint endPoint = Utilities.buildServiceEndPoint(hashName);
                            _serviceCache.evict(endPoint);
                            _chaosMeter.mark();
                            Utilities.sleepForSeconds(_chaosInterval - sleepTime);
                            _serviceCache.register(endPoint);
                            _stableMeter.mark();
                        }
                        catch(Exception ignored) {
                            ignored.printStackTrace();
                        }
                    }
                }
            };
            chaosWorkersBuilder.add(new Thread(runnable));
        }
        return chaosWorkersBuilder.build();
    }


}
