package com.bazaarvoice.soa.zookeeper;

import org.codehaus.jackson.annotate.JsonProperty;

import static com.google.common.base.Preconditions.checkArgument;

public class RetryUntilElapsed extends RetryPolicy {
    public RetryUntilElapsed(@JsonProperty("maxElapsedTimeMs") int maxElapsedTimeMs,
                             @JsonProperty("sleepMsBetweenRetries") int sleepMsBetweenRetries) {
        super(new com.netflix.curator.retry.RetryUntilElapsed(maxElapsedTimeMs, sleepMsBetweenRetries));
        checkArgument(maxElapsedTimeMs >= 0);
        checkArgument(maxElapsedTimeMs == 0 || sleepMsBetweenRetries > 0);
    }
}
