package com.bazaarvoice.soa.zookeeper;

import org.codehaus.jackson.annotate.JsonProperty;

import static com.google.common.base.Preconditions.checkArgument;

public class ExponentialBackoffRetry extends RetryPolicy {
    public ExponentialBackoffRetry(@JsonProperty("baseSleepTimeMs") int baseSleepTimeMs,
                                   @JsonProperty("maxRetries") int maxRetries) {
        super(new com.netflix.curator.retry.ExponentialBackoffRetry(baseSleepTimeMs, maxRetries));
        checkArgument(maxRetries >= 0);
        checkArgument(maxRetries == 0 || baseSleepTimeMs > 0);
    }
}
