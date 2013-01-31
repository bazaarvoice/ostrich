package com.bazaarvoice.ostrich.examples.calculator.service;

import com.bazaarvoice.curator.dropwizard.ManagedCuratorFramework;
import com.bazaarvoice.ostrich.ServiceEndPoint;
import com.bazaarvoice.ostrich.ServiceEndPointBuilder;
import com.bazaarvoice.ostrich.registry.zookeeper.ZooKeeperServiceRegistry;
import com.bazaarvoice.ostrich.registry.zookeeper.ZooKeeperServiceRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.netflix.curator.framework.CuratorFramework;
import com.netflix.curator.framework.CuratorFrameworkFactory;
import com.yammer.dropwizard.Service;
import com.yammer.dropwizard.config.Bootstrap;
import com.yammer.dropwizard.config.Environment;
import com.yammer.dropwizard.lifecycle.Managed;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import java.net.InetAddress;
import java.net.URI;
import java.util.Map;

/**
 * A Dropwizard+Jersey-based client of a simple calculator service.
 */
public class CalculatorService extends Service<CalculatorConfiguration> {
    public static Response.Status STATUS_OVERRIDE = Response.Status.OK;

    @Override
    public void initialize(Bootstrap<CalculatorConfiguration> bootstrap) {
        bootstrap.setName("calculator");
    }

    @Override
    public void run(CalculatorConfiguration config, Environment env) throws Exception {
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
                .withServiceName(env.getName())
                .withId(host + ":" + port)
                .withPayload(getJson(env).writeValueAsString(payload))
                .build();

        final CuratorFramework curator = newCurator(config.getZooKeeperConfiguration(), env);
        env.manage(new Managed() {
            ZooKeeperServiceRegistry registry = new ZooKeeperServiceRegistry(curator);

            @Override
            public void start() throws Exception {
                registry.register(endPoint);
            }

            @Override
            public void stop() throws Exception {
                registry.unregister(endPoint);
            }
        });
    }

    private ObjectMapper getJson(Environment env) {
        return env.getObjectMapperFactory().build();
    }

    private static CuratorFramework newCurator(ZooKeeperConfiguration config, Environment env) {
        CuratorFramework curator = CuratorFrameworkFactory.newClient(config.getConnectString(), config.getRetry());
        if (!Strings.isNullOrEmpty(config.getNamespace())) {
            curator = curator.usingNamespace(config.getNamespace());
        }

        env.manage(new ManagedCuratorFramework(curator));
        return curator;
    }

    public static void main(String[] args) throws Exception {
        new CalculatorService().run(args);
    }
}
