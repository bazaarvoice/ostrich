package com.bazaarvoice.ostrich.examples.dictionary.service;

import com.bazaarvoice.curator.dropwizard.ZooKeeperConfiguration;
import com.bazaarvoice.ostrich.examples.dictionary.client.WordRange;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.yammer.dropwizard.config.Configuration;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.io.File;

public class DictionaryConfiguration extends Configuration {
    @Valid
    @NotNull
    @JsonProperty
    private File wordFile = new File("/usr/share/dict/words");

    @Valid
    @NotNull
    @JsonProperty
    private WordRange wordRange = new WordRange("-");

    @Valid
    @NotNull
    @JsonProperty
    private ZooKeeperConfiguration zookeeper = new ZooKeeperConfiguration();

    public File getWordFile() {
        return wordFile;
    }

    public WordRange getWordRange() {
        return wordRange;
    }

    public ZooKeeperConfiguration getZooKeeperConfiguration() {
        return zookeeper;
    }
}
