package com.bazaarvoice.soa.discovery.server;

import com.bazaarvoice.badger.api.BadgerRegistrationBuilder;
import com.bazaarvoice.chameleon.Chameleon;
import com.bazaarvoice.chameleon.Resources;
import com.bazaarvoice.soa.discovery.ZooKeeperServiceDiscovery;
import com.bazaarvoice.soa.discovery.server.resources.ServicesResource;
import com.bazaarvoice.zookeeper.ZooKeeperConnection;
import com.bazaarvoice.zookeeper.dropwizard.ManagedZooKeeperConnection;
import com.bazaarvoice.zookeeper.dropwizard.ZooKeeperHealthCheck;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.yammer.dropwizard.Service;
import com.yammer.dropwizard.config.Bootstrap;
import com.yammer.dropwizard.config.Environment;

public class HostDiscoveryServer extends Service<HostDiscoveryServerConfiguration> {
    public static void main(String[] args) throws Exception {
        new HostDiscoveryServer().run(args);
    }

    @Override
    public void initialize(Bootstrap<HostDiscoveryServerConfiguration> bootstrap) {
        bootstrap.setName("discovery-server");
    }

    @Override
    public void run(HostDiscoveryServerConfiguration config, Environment env) throws Exception {
        Injector injector = createInjector(config);

        // Don't bother registering with Badger unless we're in AWS
        if (Chameleon.RESOURCES.HOSTING_PROVIDER.getValue() == Resources.HostingProvider.AWS) {
            ZooKeeperConnection zookeeper = injector.getInstance(ZooKeeperConnection.class);

            new BadgerRegistrationBuilder(zookeeper, env.getName())
                    .withVerificationPath(config.getHttpConfiguration().getAdminPort(), "/healthcheck")
                    .withVersion(this.getClass().getPackage().getImplementationVersion())
                    .withAwsTags()
                    .register();
        }

        env.manage(new ManagedZooKeeperConnection(injector.getInstance(ZooKeeperConnection.class)));
        env.addHealthCheck(new ZooKeeperHealthCheck(injector.getInstance(ZooKeeperConnection.class)));
        env.addResource(injector.getInstance(ServicesResource.class));
    }

    private static Injector createInjector(final HostDiscoveryServerConfiguration config) {
        return Guice.createInjector(new AbstractModule() {
            @Override
            protected void configure() {
                bind(ZooKeeperHostDiscoveryFactory.class).asEagerSingleton();
                bind(ServicesResource.class).asEagerSingleton();
            }

            @Provides
            @Singleton
            ZooKeeperConnection provideZooKeeperConnection() {
                return config.getZookeeperConfiguration().connect();
            }

            @Provides
            @Singleton
            ZooKeeperServiceDiscovery provideZooKeeperServiceDiscovery(ZooKeeperConnection zookeeper) {
                return new ZooKeeperServiceDiscovery(zookeeper);
            }
        });
    }
}
