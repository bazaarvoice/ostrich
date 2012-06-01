package com.bazaarvoice.soa.zookeeper;

class RetryPolicy {
    private final com.netflix.curator.RetryPolicy _retryPolicy;

    RetryPolicy(com.netflix.curator.RetryPolicy retryPolicy) {
        _retryPolicy = retryPolicy;
    }

    com.netflix.curator.RetryPolicy get() {
        return _retryPolicy;
    }
}
