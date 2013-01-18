package com.bazaarvoice.soa.discovery.server;

import com.bazaarvoice.zookeeper.dropwizard.ZooKeeperConfiguration;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.yammer.dropwizard.config.Configuration;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

public class HostDiscoveryServerConfiguration extends Configuration {
    @Valid
    @NotNull
    @JsonProperty
    private final ZooKeeperConfiguration zookeeper = new ZooKeeperConfiguration();

    public ZooKeeperConfiguration getZookeeperConfiguration() {
        return zookeeper;
    }
}
