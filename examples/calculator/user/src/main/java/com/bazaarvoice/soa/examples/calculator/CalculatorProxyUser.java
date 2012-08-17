package com.bazaarvoice.soa.examples.calculator;

import com.bazaarvoice.soa.dropwizard.healthcheck.ContainsHealthyEndPointCheck;
import com.bazaarvoice.soa.pool.ServicePoolProxies;
import com.bazaarvoice.soa.pool.ServiceCachingPolicy;
import com.bazaarvoice.soa.pool.ServiceCachingPolicyBuilder;
import com.bazaarvoice.soa.pool.ServicePoolBuilder;
import com.bazaarvoice.soa.retry.RetryNTimes;
import com.bazaarvoice.zookeeper.ZooKeeperConfiguration;
import com.bazaarvoice.zookeeper.ZooKeeperConnection;
import com.google.common.io.Closeables;
import com.yammer.dropwizard.config.ConfigurationFactory;
import com.yammer.dropwizard.validation.Validator;
import com.yammer.metrics.HealthChecks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * Variation of {@link CalculatorUser} that uses a dynamic service proxy instead of making direct calls to
 * {@link com.bazaarvoice.soa.ServicePool}.
 */
public class CalculatorProxyUser {
    private static final Logger LOG = LoggerFactory.getLogger(CalculatorProxyUser.class);

    private final Random _random = new Random();
    private final CalculatorService _service;

    public CalculatorProxyUser(CalculatorService service) {
        _service = service;
    }

    public void use() throws InterruptedException {
        int i = 0;
        while (++i > 0) {
            try {
                int a = _random.nextInt(10);
                int b = 1 + _random.nextInt(9);
                int op = _random.nextInt(4);
                int result = call(op, a, b);
                LOG.info("i:{}, result:{}", i, result);
            } catch (Exception e) {
                LOG.info("i:{}, {}", i, e.getClass().getCanonicalName());
            }

            Thread.sleep(500);
        }
    }

    private int call(int op, int a, int b) {
        switch (op) {
            case 0:  return _service.add(a, b);
            case 1:  return _service.sub(a, b);
            case 2:  return _service.mul(a, b);
            default: return _service.div(a, b);
        }
    }

    public static void main(String[] args) throws Exception {
        // Load the config.yaml file specified as the first argument.  Or just use defaults if none specified.
        CalculatorConfiguration configuration;
        if (args.length > 0) {
            ConfigurationFactory<CalculatorConfiguration> configFactory = ConfigurationFactory.forClass(
                    CalculatorConfiguration.class, new Validator());
            configuration = configFactory.build(new File(args[0]));
        } else {
            configuration = new CalculatorConfiguration();
        }

        ZooKeeperConnection zooKeeper = configuration.getZooKeeperConfiguration().connect();

        // Connection caching is optional, but included here for the sake of demonstration.
        ServiceCachingPolicy cachingPolicy = new ServiceCachingPolicyBuilder()
                .withMaxNumServiceInstances(10)
                .withMaxNumServiceInstancesPerEndPoint(1)
                .withMaxServiceInstanceIdleTime(5, TimeUnit.MINUTES)
                .build();

        CalculatorService service = ServicePoolBuilder.create(CalculatorService.class)
                .withServiceFactory(new CalculatorServiceFactory(configuration.getHttpClientConfiguration()))
                .withZooKeeperHostDiscovery(zooKeeper)
                .withCachingPolicy(cachingPolicy)
                .buildProxy(new RetryNTimes(3, 100, TimeUnit.MILLISECONDS));

        // If using Yammer Metrics or running in Dropwizard (which includes Yammer Metrics), you may want a health
        // check that pings a service you depend on. This will register a simple check that will confirm the service
        // pool contains at least one healthy end point.
        HealthChecks.register(new ContainsHealthyEndPointCheck(ServicePoolProxies.getPool(service), "calculator-user"));

        CalculatorProxyUser user = new CalculatorProxyUser(service);
        user.use();

        ServicePoolProxies.close(service);
        Closeables.closeQuietly(zooKeeper);
    }
}
