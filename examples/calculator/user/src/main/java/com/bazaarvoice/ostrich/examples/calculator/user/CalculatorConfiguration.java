package com.bazaarvoice.ostrich.examples.calculator.user;

import com.bazaarvoice.curator.dropwizard.ZooKeeperConfiguration;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.yammer.dropwizard.client.JerseyClientConfiguration;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

/**
 * YAML-friendly configuration class.
 */
public class CalculatorConfiguration {
    @NotNull
    @Valid
    @JsonProperty
    private final ZooKeeperConfiguration zookeeper = new ZooKeeperConfiguration();

    @Valid
    @NotNull
    @JsonProperty
    private JerseyClientConfiguration httpClient = new JerseyClientConfiguration();

    public ZooKeeperConfiguration getZooKeeperConfiguration() {
        return zookeeper;
    }

    public JerseyClientConfiguration getHttpClientConfiguration() {
        return httpClient;
    }
}
