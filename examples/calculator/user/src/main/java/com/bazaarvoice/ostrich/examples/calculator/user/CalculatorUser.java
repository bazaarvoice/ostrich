package com.bazaarvoice.ostrich.examples.calculator.user;

import com.bazaarvoice.ostrich.ServiceCallback;
import com.bazaarvoice.ostrich.ServicePool;
import com.bazaarvoice.ostrich.discovery.zookeeper.ZooKeeperHostDiscovery;
import com.bazaarvoice.ostrich.dropwizard.healthcheck.ContainsHealthyEndPointCheck;
import com.bazaarvoice.ostrich.examples.calculator.client.CalculatorService;
import com.bazaarvoice.ostrich.examples.calculator.client.CalculatorServiceFactory;
import com.bazaarvoice.ostrich.exceptions.ServiceException;
import com.bazaarvoice.ostrich.pool.ServiceCachingPolicy;
import com.bazaarvoice.ostrich.pool.ServiceCachingPolicyBuilder;
import com.bazaarvoice.ostrich.pool.ServicePoolBuilder;
import com.bazaarvoice.ostrich.retry.ExponentialBackoffRetry;
import com.google.common.io.Closeables;
import com.netflix.curator.framework.CuratorFramework;
import com.yammer.dropwizard.config.ConfigurationException;
import com.yammer.dropwizard.config.ConfigurationFactory;
import com.yammer.dropwizard.util.JarLocation;
import com.yammer.dropwizard.validation.Validator;
import com.yammer.metrics.HealthChecks;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
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
                int result = _calculatorPool.execute(new ExponentialBackoffRetry(5, 50, 1000, TimeUnit.MILLISECONDS),
                        new ServiceCallback<CalculatorService, Integer>() {
                            @Override
                            public Integer call(CalculatorService service) throws ServiceException {
                                return CalculatorUser.this.call(service, op, a, b);
                            }
                        });
                LOG.info("i:{}, result:{}", i, result);
            } catch (Exception e) {
                LOG.warn("i:{}, {}", i, e);
            }

            Thread.sleep(100);
        }
    }

    private int call(CalculatorService service, int op, int a, int b) {
        switch (op) {
            case 0:  return service.add(a, b);
            case 1:  return service.sub(a, b);
            case 2:  return service.mul(a, b);
            default: return service.div(a, b);
        }
    }

    public static void main(String[] args) throws Exception {
        Namespace parsedArgs = parseCommandLine(args);

        // Load the config.yaml file specified as the first argument (optional).
        CalculatorConfiguration config = loadConfigFile(parsedArgs.getString("config-file"));

        // Connect to ZooKeeper
        CuratorFramework curator = config.getZooKeeperConfiguration().newCurator();
        curator.start();

        // Connection caching is optional, but included here for the sake of demonstration.
        ServiceCachingPolicy cachingPolicy = new ServiceCachingPolicyBuilder()
                .withMaxNumServiceInstances(10)
                .withMaxNumServiceInstancesPerEndPoint(1)
                .withMaxServiceInstanceIdleTime(5, TimeUnit.MINUTES)
                .build();

        CalculatorServiceFactory serviceFactory = new CalculatorServiceFactory(config.getHttpClientConfiguration());
        ServicePool<CalculatorService> pool = ServicePoolBuilder.create(CalculatorService.class)
                .withServiceFactory(serviceFactory)
                .withHostDiscovery(new ZooKeeperHostDiscovery(curator, serviceFactory.getServiceName()))
                .withCachingPolicy(cachingPolicy)
                .build();

        // If using Yammer Metrics or running in Dropwizard (which includes Yammer Metrics), you may want a health
        // check that pings a service you depend on. This will register a simple check that will confirm the service
        // pool contains at least one healthy end point.
        HealthChecks.register(new ContainsHealthyEndPointCheck(pool, "calculator-user"));

        CalculatorUser user = new CalculatorUser(pool);
        user.use();

        Closeables.closeQuietly(pool);
        Closeables.closeQuietly(curator);
    }

    private static Namespace parseCommandLine(String[] args) throws ArgumentParserException {
        String usage = "java -jar " + new JarLocation(CalculatorUser.class);
        ArgumentParser argParser = ArgumentParsers.newArgumentParser(usage).defaultHelp(true);
        argParser.addArgument("config-file").nargs("?").help("yaml configuration file");
        return argParser.parseArgs(args);
    }

    private static CalculatorConfiguration loadConfigFile(String configFile)
            throws IOException, ConfigurationException {
        ConfigurationFactory<CalculatorConfiguration> configFactory = ConfigurationFactory
                .forClass(CalculatorConfiguration.class, new Validator());
        if (configFile != null) {
            return configFactory.build(new File(configFile));
        } else {
            return configFactory.build();
        }
    }
}
