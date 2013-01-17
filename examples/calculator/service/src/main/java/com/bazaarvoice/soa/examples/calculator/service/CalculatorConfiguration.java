package com.bazaarvoice.soa.examples.calculator.service;

import com.bazaarvoice.zookeeper.dropwizard.ZooKeeperConfiguration;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.yammer.dropwizard.config.Configuration;

public class CalculatorConfiguration extends Configuration {

    private ZooKeeperConfiguration _zooKeeperConfiguration = new ZooKeeperConfiguration();

    public ZooKeeperConfiguration getZooKeeperConfiguration() {
        return _zooKeeperConfiguration;
    }

    @JsonProperty("zooKeeper")
    public void setZooKeeperConfiguration(ZooKeeperConfiguration zooKeeperConfiguration) {
        _zooKeeperConfiguration = zooKeeperConfiguration;
    }
}
