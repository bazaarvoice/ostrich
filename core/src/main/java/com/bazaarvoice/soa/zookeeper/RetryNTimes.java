package com.bazaarvoice.soa.zookeeper;

import org.codehaus.jackson.annotate.JsonProperty;

import static com.google.common.base.Preconditions.checkArgument;

public class RetryNTimes extends RetryPolicy {
    public RetryNTimes(@JsonProperty("n") int n,
                       @JsonProperty("sleepMsBetweenRetries") int sleepMsBetweenRetries) {
        super(new com.netflix.curator.retry.RetryNTimes(n, sleepMsBetweenRetries));
        checkArgument(n >= 0);
        checkArgument(n == 0 || sleepMsBetweenRetries > 0);
    }
}
