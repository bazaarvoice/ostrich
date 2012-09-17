package com.bazaarvoice.soa.examples.dictionary.service;

import com.bazaarvoice.soa.ServiceEndPoint;
import com.bazaarvoice.soa.ServiceEndPointBuilder;
import com.bazaarvoice.soa.ServiceRegistry;
import com.bazaarvoice.soa.registry.ZooKeeperServiceRegistry;
import com.bazaarvoice.zookeeper.ZooKeeperConnection;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Closeables;
import com.yammer.dropwizard.Service;
import com.yammer.dropwizard.config.Environment;
import com.yammer.dropwizard.lifecycle.Managed;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import java.net.InetAddress;
import java.net.URI;
import java.util.Map;

/**
 * A Dropwizard+Jersey-based client of a simple dictionary service.
 */
public class DictionaryService extends Service<DictionaryConfiguration> {
    public static Response.Status STATUS_OVERRIDE = Response.Status.OK;

    public DictionaryService() {
        super("dictionary");
    }

    @Override
    protected void initialize(DictionaryConfiguration config, Environment env) throws Exception {
        // Load the subset of the dictionary handled by this server.
        WordList wordList = new WordList(config.getWordFile(), config.getWordRange());

        env.addResource(new DictionaryResource(wordList, config.getWordRange()));
        env.addResource(ToggleHealthResource.class);
        env.addProvider(new IllegalArgumentExceptionMapper());
        env.addHealthCheck(new DictionaryHealthCheck());

        InetAddress localhost = InetAddress.getLocalHost();
        String host = localhost.getHostName();
        String ip = localhost.getHostAddress();
        int port = config.getHttpConfiguration().getPort();
        int adminPort = config.getHttpConfiguration().getAdminPort();

        // The client reads the URLs out of the payload to figure out how to connect to this server.
        URI serviceUri = UriBuilder.fromResource(DictionaryResource.class).scheme("http").host(ip).port(port).build();
        URI adminUri = UriBuilder.fromPath("").scheme("http").host(ip).port(adminPort).build();
        Map<String, ?> payload = ImmutableMap.of(
                "url", serviceUri,
                "adminUrl", adminUri,
                "partition", config.getWordRange());
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
        new DictionaryService().run(args);
    }
}
