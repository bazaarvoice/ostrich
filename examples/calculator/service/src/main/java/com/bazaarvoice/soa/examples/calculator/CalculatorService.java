package com.bazaarvoice.soa.examples.calculator;

import com.bazaarvoice.soa.ServiceEndPoint;
import com.bazaarvoice.soa.ServiceEndPointBuilder;
import com.bazaarvoice.soa.ServiceRegistry;
import com.bazaarvoice.soa.registry.ZooKeeperServiceRegistry;
import com.bazaarvoice.soa.zookeeper.ZooKeeperConnection;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Closeables;
import com.yammer.dropwizard.Service;
import com.yammer.dropwizard.config.Environment;
import com.yammer.dropwizard.lifecycle.Managed;

import javax.ws.rs.core.UriBuilder;
import java.net.InetAddress;
import java.net.URI;
import java.util.Map;

/**
 * A Dropwizard+Jersey-based client of a simple calculator service.
 */
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

        InetAddress localhost = InetAddress.getLocalHost();
        String host = localhost.getHostName();
        String ip = localhost.getHostAddress();
        int port = config.getHttpConfiguration().getPort();
        int adminPort = config.getHttpConfiguration().getAdminPort();

        // The client reads the URLs out of the payload to figure out how to connect to this server.
        URI serviceUri = UriBuilder.fromResource(CalculatorResource.class).scheme("http").host(ip).port(port).build();
        URI adminUri = UriBuilder.fromPath("").scheme("http").host(ip).port(adminPort).build();
        Map<String, ?> payload = ImmutableMap.of(
                "url", serviceUri,
                "adminUrl", adminUri);
        final ServiceEndPoint endPoint = new ServiceEndPointBuilder()
                .withServiceName(getName())
                .withId(host + ":" + port)
                .withPayload(getJson().writeValueAsString(payload))
                .build();

        // Once everything has initialized successfully, register services with ZooKeeper where clients can find them.
        final ZooKeeperConnection connection = config.getZooKeeperConfiguration().connect();
        final ServiceRegistry registry = new ZooKeeperServiceRegistry(connection);
        env.manage(new Managed() {
            @Override
            public void start() throws Exception {
                registry.register(endPoint);
            }

            @Override
            public void stop() throws Exception {
                registry.unregister(endPoint);
                Closeables.closeQuietly(connection);
            }
        });
    }

    public static void main(String[] args) throws Exception {
        new CalculatorService().run(args);
    }
}
