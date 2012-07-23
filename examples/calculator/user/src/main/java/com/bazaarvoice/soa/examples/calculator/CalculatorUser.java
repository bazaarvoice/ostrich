package com.bazaarvoice.soa.examples.calculator;

import com.bazaarvoice.soa.ServiceCallback;
import com.bazaarvoice.soa.ServicePool;
import com.bazaarvoice.soa.exceptions.ServiceException;
import com.bazaarvoice.soa.pool.ServiceCachingPolicy;
import com.bazaarvoice.soa.pool.ServiceCachingPolicyBuilder;
import com.bazaarvoice.soa.pool.ServicePoolBuilder;
import com.bazaarvoice.soa.retry.RetryNTimes;
import com.bazaarvoice.soa.zookeeper.ZooKeeperConfiguration;
import com.bazaarvoice.soa.zookeeper.ZooKeeperConnection;
import com.google.common.io.Closeables;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Random;
import java.util.concurrent.TimeUnit;

public class CalculatorUser {
    private static final Logger LOG = LoggerFactory.getLogger(CalculatorUser.class);

    private final Random _random = new Random();
    private final ServicePool<CalculatorService> _calculatorPool;

    public CalculatorUser(ServicePool<CalculatorService> calculatorPool) {
        _calculatorPool = calculatorPool;
    }

    public void use() throws InterruptedException {
        int i = 0;
        while (++i > 0) {
            try {
                final int a = _random.nextInt(10);
                final int b = 1 + _random.nextInt(9);
                final int op = _random.nextInt(4);
                int result = _calculatorPool.execute(new RetryNTimes(3, 100, TimeUnit.MILLISECONDS),
                        new ServiceCallback<CalculatorService, Integer>() {
                            @Override
                            public Integer call(CalculatorService service) throws ServiceException {
                                switch (op) {
                                    case 0:  return service.add(a, b);
                                    case 1:  return service.sub(a, b);
                                    case 2:  return service.mul(a, b);
                                    default: return service.div(a, b);
                                }
                            }
                        });
                LOG.info("i:{}, result:{}", i, result);
            } catch (Exception e) {
                LOG.info("i:{}, {}", i, e.getClass().getCanonicalName());
            }

            Thread.sleep(500);
        }
    }

    public static void main(String[] args) throws InterruptedException, IOException {
        String connectString = (args.length > 0) ? args[0] : "localhost:2181";

        ZooKeeperConnection connection = new ZooKeeperConfiguration()
                .withConnectString(connectString)
                .withBoundedExponentialBackoffRetry(10, 1000, 3)
                .connect();

        ServiceCachingPolicy cachingPolicy = new ServiceCachingPolicyBuilder()
                .withMaxNumServiceInstances(10)
                .withMaxNumServiceInstancesPerEndPoint(1)
                .withMaxServiceInstanceIdleTime(5, TimeUnit.MINUTES)
                .build();

        ServicePool<CalculatorService> pool = ServicePoolBuilder.create(CalculatorService.class)
                .withZooKeeperHostDiscovery(connection)
                .withServiceFactory(new CalculatorServiceFactory())
                .withCachingPolicy(cachingPolicy)
                .build();

        CalculatorUser user = new CalculatorUser(pool);
        user.use();

        Closeables.closeQuietly(pool);
        Closeables.closeQuietly(connection);
    }
}
