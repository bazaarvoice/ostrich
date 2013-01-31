package com.bazaarvoice.ostrich.examples.calculator.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.netflix.curator.RetryPolicy;
import com.netflix.curator.retry.BoundedExponentialBackoffRetry;
import org.hibernate.validator.constraints.NotEmpty;

import javax.validation.constraints.NotNull;

public class ZooKeeperConfiguration {
    @NotNull
    @NotEmpty
    @JsonProperty("connect-string")
    private final String _connectString = "localhost:2181";

    @JsonProperty("namespace")
    private final String _namespace = null;

    @NotNull
    @JsonProperty("retry")
    private Retry _retry = null;

    public String getConnectString() {
        return _connectString;
    }

    public String getNamespace() {
        return _namespace;
    }

    public RetryPolicy getRetry() {
        return new BoundedExponentialBackoffRetry(_retry.baseSleepTimeMs, _retry.maxSleepTimeMs, _retry.maxAttempts);
    }

    private static final class Retry {
        @NotEmpty
        @NotNull
        @JsonProperty
        private final Integer baseSleepTimeMs = null;

        @NotEmpty
        @NotNull
        @JsonProperty
        private final Integer maxSleepTimeMs = null;

        @NotEmpty
        @NotNull
        @JsonProperty
        private final Integer maxAttempts = null;
    }
}
