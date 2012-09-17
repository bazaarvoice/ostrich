package com.bazaarvoice.soa.examples.dictionary.service;

import com.bazaarvoice.soa.examples.dictionary.client.WordRange;
import com.bazaarvoice.zookeeper.dropwizard.ZooKeeperConfiguration;
import com.yammer.dropwizard.config.Configuration;
import org.codehaus.jackson.annotate.JsonProperty;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.io.File;

public class DictionaryConfiguration extends Configuration {

    @Valid
    @NotNull
    @JsonProperty("wordFile")
    private File wordFile = new File("/usr/share/dict/words");

    @Valid
    @NotNull
    @JsonProperty("wordRange")
    private WordRange wordRange = new WordRange("-");

    @Valid
    @NotNull
    @JsonProperty("zooKeeper")
    private ZooKeeperConfiguration zooKeeperConfiguration = new ZooKeeperConfiguration();

    public File getWordFile() {
        return wordFile;
    }

    public void setWordFile(File wordFile) {
        this.wordFile = wordFile;
    }

    public WordRange getWordRange() {
        return wordRange;
    }

    public void setWordRange(WordRange wordRange) {
        this.wordRange = wordRange;
    }

    public ZooKeeperConfiguration getZooKeeperConfiguration() {
        return zooKeeperConfiguration;
    }

    public void setZooKeeperConfiguration(ZooKeeperConfiguration zooKeeperConfiguration) {
        this.zooKeeperConfiguration = zooKeeperConfiguration;
    }
}
