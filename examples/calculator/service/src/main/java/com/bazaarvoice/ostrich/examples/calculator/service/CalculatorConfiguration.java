package com.bazaarvoice.ostrich.examples.calculator.service;

import com.bazaarvoice.curator.dropwizard.ZooKeeperConfiguration;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.yammer.dropwizard.config.Configuration;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

public class CalculatorConfiguration extends Configuration {
    @NotNull
    @Valid
    @JsonProperty
    private final ZooKeeperConfiguration zooKeeper = new ZooKeeperConfiguration();

    public ZooKeeperConfiguration getZooKeeperConfiguration() {
        return zooKeeper;
    }
}
