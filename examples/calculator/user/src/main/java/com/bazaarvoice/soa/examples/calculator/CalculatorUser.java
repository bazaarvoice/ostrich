package com.bazaarvoice.soa.examples.calculator;

import com.bazaarvoice.soa.ServiceCallback;
import com.bazaarvoice.soa.ServicePool;
import com.bazaarvoice.soa.exceptions.ServiceException;
import com.bazaarvoice.soa.pool.ServicePoolBuilder;
import com.bazaarvoice.soa.retry.RetryNTimes;
import com.bazaarvoice.soa.zookeeper.ZooKeeperConfiguration;
import com.bazaarvoice.soa.zookeeper.ZooKeeperConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;
import java.util.concurrent.TimeUnit;

public class CalculatorUser {
    private final Logger LOG = LoggerFactory.getLogger(getClass());
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

    public static void main(String[] args) throws InterruptedException {
        String connectString = (args.length > 0) ? args[0] : "localhost:2181";

        ZooKeeperConnection connection = new ZooKeeperConfiguration()
                .setConnectString(connectString)
                .setRetryNTimes(new com.bazaarvoice.soa.zookeeper.RetryNTimes(3, 100))
                .connect();

        ServicePool<CalculatorService> pool = new ServicePoolBuilder<CalculatorService>()
                .withZooKeeperHostDiscovery(connection)
                .withServiceFactory(new CalculatorServiceFactory())
                .build();

        CalculatorUser user = new CalculatorUser(pool);
        user.use();
    }
}
