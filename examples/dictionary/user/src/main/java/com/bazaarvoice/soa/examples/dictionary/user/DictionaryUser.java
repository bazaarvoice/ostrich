package com.bazaarvoice.soa.examples.dictionary.user;

import com.bazaarvoice.soa.dropwizard.healthcheck.ContainsHealthyEndPointCheck;
import com.bazaarvoice.soa.examples.dictionary.client.DictionaryService;
import com.bazaarvoice.soa.examples.dictionary.client.DictionaryServiceFactory;
import com.bazaarvoice.soa.pool.ServiceCachingPolicy;
import com.bazaarvoice.soa.pool.ServiceCachingPolicyBuilder;
import com.bazaarvoice.soa.pool.ServicePoolBuilder;
import com.bazaarvoice.soa.pool.ServicePoolProxies;
import com.bazaarvoice.soa.retry.ExponentialBackoffRetry;
import com.bazaarvoice.zookeeper.ZooKeeperConnection;
import com.google.common.base.CharMatcher;
import com.google.common.base.Charsets;
import com.google.common.base.Splitter;
import com.google.common.io.Closeables;
import com.google.common.io.Files;
import com.google.common.io.LineProcessor;
import com.yammer.dropwizard.config.ConfigurationFactory;
import com.yammer.dropwizard.validation.Validator;
import com.yammer.metrics.HealthChecks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class DictionaryUser {
    private static final Logger LOG = LoggerFactory.getLogger(DictionaryUser.class);

    private static final CharMatcher LETTER = CharMatcher.inRange('a', 'z').or(CharMatcher.inRange('A', 'Z'));

    private final DictionaryService _service;

    public DictionaryUser(DictionaryService service) {
        _service = service;
    }

    public void spellCheck(File file) throws IOException {
        LOG.info("Spell checking file: {}", file);
        Files.readLines(file, Charsets.UTF_8, new LineProcessor<Void>() {
            @Override
            public boolean processLine(String line) throws IOException {
                for (String word : Splitter.on(CharMatcher.WHITESPACE).split(line)) {
                    // Discard punctuation, numbers, etc.
                    word = LETTER.retainFrom(word);
                    if (!word.isEmpty()) {
                        spellCheck(word);
                    }
                }
                return true;
            }

            @Override
            public Void getResult() {
                return null;
            }
        });
    }

    public void spellCheck(String word) {
        try {
            if (_service.contains(word)) {
                LOG.info("ok: {}", word);
            } else {
                LOG.info("MISSPELLED: {}", word);
            }
        } catch (Exception e) {
            LOG.warn("word:{}, {}", word, e);
        }
    }

    public static void main(String[] args) throws Exception {
        // Load the config.yaml file specified as the first argument.  Remaining arguments are files to spell check.
        if (args.length < 2) {
            System.err.println("usage: DictionaryUser config.yaml <word-file> ...");
        }

        ConfigurationFactory<DictionaryConfiguration> configFactory = ConfigurationFactory.forClass(
                DictionaryConfiguration.class, new Validator());
        DictionaryConfiguration configuration = configFactory.build(new File(args[0]));

        ZooKeeperConnection zooKeeper = configuration.getZooKeeperConfiguration().connect();

        // Connection caching is optional, but included here for the sake of demonstration.
        ServiceCachingPolicy cachingPolicy = new ServiceCachingPolicyBuilder()
                .withMaxNumServiceInstances(10)
                .withMaxNumServiceInstancesPerEndPoint(1)
                .withMaxServiceInstanceIdleTime(5, TimeUnit.MINUTES)
                .build();

        // The service is partitioned, but partition filtering is configured by the ServiceFactory in this case
        // when the builder calls its configure() method.
        DictionaryService service = ServicePoolBuilder.create(DictionaryService.class)
                .withServiceFactory(new DictionaryServiceFactory(configuration.getHttpClientConfiguration()))
                .withZooKeeperHostDiscovery(zooKeeper)
                .withCachingPolicy(cachingPolicy)
                .buildProxy(new ExponentialBackoffRetry(5, 50, 1000, TimeUnit.MILLISECONDS));

        // If using Yammer Metrics or running in Dropwizard (which includes Yammer Metrics), you may want a health
        // check that pings a service you depend on. This will register a simple check that will confirm the service
        // pool contains at least one healthy end point.
        HealthChecks.register(new ContainsHealthyEndPointCheck(ServicePoolProxies.getPool(service), "dictionary-user"));

        DictionaryUser user = new DictionaryUser(service);
        for (int i = 1; i < args.length; i++) {
            user.spellCheck(new File(args[i]));
        }

        ServicePoolProxies.close(service);
        Closeables.closeQuietly(zooKeeper);
    }
}
