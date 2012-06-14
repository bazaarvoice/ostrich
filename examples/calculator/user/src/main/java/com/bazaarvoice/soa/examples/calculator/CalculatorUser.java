package com.bazaarvoice.soa.examples.calculator;

import com.bazaarvoice.soa.ServiceCallback;
import com.bazaarvoice.soa.exceptions.ServiceException;
import com.bazaarvoice.soa.ServicePool;
import com.bazaarvoice.soa.discovery.ZooKeeperHostDiscovery;
import com.bazaarvoice.soa.pool.ServicePoolBuilder;
import com.bazaarvoice.soa.retry.RetryNTimes;
import com.bazaarvoice.soa.zookeeper.ZooKeeperConfiguration;
import com.bazaarvoice.soa.zookeeper.ZooKeeperConnection;
import com.google.common.util.concurrent.ThreadFactoryBuilder;

import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

public class CalculatorUser {
    private final ServicePool<CalculatorService> _calculatorPool;

    public CalculatorUser(ServicePool<CalculatorService> calculatorPool) {
        _calculatorPool = calculatorPool;
    }

    public void use() throws InterruptedException {
        final Random rnd = new Random();
        int i = 0;

        //noinspection InfiniteLoopStatement
        while (true) {
            try {
                int result = _calculatorPool.execute(new RetryNTimes(3, 100, TimeUnit.MILLISECONDS),
                        new ServiceCallback<CalculatorService, Integer>() {
                            @Override
                            public Integer call(CalculatorService service) throws ServiceException {
                                int a = rnd.nextInt(10);
                                int b = 1 + rnd.nextInt(9);
                                int op = rnd.nextInt(4);
                                if (op == 0) {
                                    return service.add(a, b);
                                } else if (op == 1) {
                                    return service.sub(a, b);
                                } else if (op == 2) {
                                    return service.mul(a, b);
                                } else {
                                    return service.div(a, b);
                                }
                            }
                        });
                System.out.println("i: " + i + ", result: " + result);
            } catch (Exception e) {
                System.out.println("i: " + i + ", " + e.getClass().getCanonicalName());
            }

            Thread.sleep(500);
            i++;
        }
    }

    public static void main(String[] args) throws InterruptedException {
        String connectString = (args.length > 0) ? args[0] : "localhost:2181";

        ZooKeeperConnection connection = new ZooKeeperConfiguration()
                .setConnectString(connectString)
                .setRetryNTimes(new com.bazaarvoice.soa.zookeeper.RetryNTimes(3, 100))
                .connect();

        ThreadFactory daemonThreadFactory = new ThreadFactoryBuilder()
                .setDaemon(true)
                .build();

        ServicePool<CalculatorService> pool = new ServicePoolBuilder<CalculatorService>()
                .withHostDiscovery(new ZooKeeperHostDiscovery(connection, "calculator"))
                .withServiceFactory(new CalculatorServiceFactory())
                .withHealthCheckExecutor(Executors.newScheduledThreadPool(1, daemonThreadFactory))
                .build();

        CalculatorUser user = new CalculatorUser(pool);
        user.use();
    }
}
