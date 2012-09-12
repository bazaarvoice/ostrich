package com.bazaarvoice.soa.examples.calculator.service;

import com.bazaarvoice.zookeeper.dropwizard.ZooKeeperConfiguration;
import com.yammer.dropwizard.config.Configuration;
import org.codehaus.jackson.annotate.JsonProperty;

public class CalculatorConfiguration extends Configuration {
    private String _ensembleName;
    private ZooKeeperConfiguration _zooKeeperConfiguration = new ZooKeeperConfiguration();

    public String getEnsembleName() {
        return _ensembleName;
    }

    @JsonProperty("ensembleName")
    public void setEnsembleName(String ensembleName) {
        _ensembleName = ensembleName;
    }

    public ZooKeeperConfiguration getZooKeeperConfiguration() {
        return _zooKeeperConfiguration;
    }

    @JsonProperty("zooKeeper")
    public void setZookeeperConfiguration(ZooKeeperConfiguration zooKeeperConfiguration) {
        _zooKeeperConfiguration = zooKeeperConfiguration;
    }
}
