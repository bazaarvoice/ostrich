package com.bazaarvoice.soa.examples.calculator;

import com.bazaarvoice.soa.ServiceEndpoint;
import com.bazaarvoice.soa.ServiceRegistry;
import com.bazaarvoice.soa.registry.ZooKeeperServiceRegistry;
import com.bazaarvoice.soa.zookeeper.ZooKeeperConnection;
import com.google.common.collect.ImmutableMap;
import com.yammer.dropwizard.Service;
import com.yammer.dropwizard.config.Environment;

import java.net.InetAddress;
import java.net.URL;
import java.util.Map;

public class CalculatorService extends Service<CalculatorConfiguration> {
    public static boolean IS_HEALTHY = true;

    public CalculatorService() {
        super("calculator");
    }

    @Override
    protected void initialize(CalculatorConfiguration config, Environment env) throws Exception {
        env.addResource(CalculatorResource.class);
        env.addResource(ToggleHealthResource.class);
        env.addHealthCheck(new CalculatorHealthCheck());

        String hostname = InetAddress.getLocalHost().getHostName();
        int port = config.getHttpConfiguration().getPort();
        int adminPort = config.getHttpConfiguration().getAdminPort();

        Map<?,?> payload = ImmutableMap.builder()
                .put("url", new URL("http", hostname, port, "/" + getName()))
                .put("adminUrl", new URL("http", hostname, adminPort, ""))
                .build();
        ServiceEndpoint endpoint = new ServiceEndpoint(getName(), hostname, port, JsonHelper.toJson(payload));

        // Register with ZooKeeper
        ZooKeeperConnection connection = config.getZooKeeperConfiguration().connect();
        ServiceRegistry registry = new ZooKeeperServiceRegistry(connection);
        registry.register(endpoint);
    }

    public static void main(String[] args) throws Exception {
        new CalculatorService().run(args);
    }
}
