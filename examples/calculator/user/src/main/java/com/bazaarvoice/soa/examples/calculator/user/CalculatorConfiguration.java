package com.bazaarvoice.soa.examples.calculator.user;

import com.bazaarvoice.zookeeper.dropwizard.ZooKeeperConfiguration;
import com.yammer.dropwizard.client.JerseyClientConfiguration;
import org.codehaus.jackson.annotate.JsonProperty;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

/**
 * YAML-friendly configuration class.
 */
public class CalculatorConfiguration {
    @Valid
    @NotNull
    @JsonProperty("zooKeeper")
    private ZooKeeperConfiguration _zooKeeperConfiguration = new ZooKeeperConfiguration();

    @Valid
    @NotNull
    @JsonProperty("httpClient")
    private JerseyClientConfiguration _httpClientConfiguration = new JerseyClientConfiguration();

    public ZooKeeperConfiguration getZooKeeperConfiguration() {
        return _zooKeeperConfiguration;
    }

    public CalculatorConfiguration setZooKeeperConfiguration(ZooKeeperConfiguration zooKeeperConfiguration) {
        _zooKeeperConfiguration = zooKeeperConfiguration;
        return this;
    }

    public JerseyClientConfiguration getHttpClientConfiguration() {
        return _httpClientConfiguration;
    }

    public CalculatorConfiguration setHttpClientConfiguration(JerseyClientConfiguration httpClientConfiguration) {
        _httpClientConfiguration = httpClientConfiguration;
        return this;
    }
}
