package com.bazaarvoice.soa.zookeeper;

import com.bazaarvoice.soa.internal.CuratorConnection;
import com.google.common.annotations.VisibleForTesting;

/**
 * ZooKeeper connection configuration class.
 * <p>
 * This class is designed to map easily to YAML configuration files, deserialized using Jackson.
 */
public class ZooKeeperConfiguration {

    private String _connectString = "localhost:2181";
    private RetryPolicy _retryPolicy = new RetryNTimes(3, 100);

    /**
     * Returns a new {@link ZooKeeperConnection} with the current configuration settings.
     * @return A new {@link ZooKeeperConnection} with the current configuration settings.
     */
    public ZooKeeperConnection connect() {
        return new CuratorConnection(_connectString, _retryPolicy.get());
    }

    @VisibleForTesting
    String getConnectString() {
        return _connectString;
    }

    /**
     * Sets a ZooKeeper connection string that looks like "host:port,host:port,...".  The
     * connection string must list at least one live member of the ZooKeeper ensemble, and
     * should list all members of the ZooKeeper ensemble in case any one member is temporarily
     * unavailable.
     * @param connectString A ZooKeeper connection string.
     */
    public ZooKeeperConfiguration setConnectString(String connectString) {
        _connectString = connectString;
        return this;
    }

    @VisibleForTesting
    RetryPolicy getRetryPolicy() {
        return _retryPolicy;
    }

    /**
     * Sets a retry policy that retries a set number of times with increasing sleep time between retries.
     */
    public ZooKeeperConfiguration setExponentialBackoffRetry(ExponentialBackoffRetry retryPolicy) {
        _retryPolicy = retryPolicy;
        return this;
    }

    /**
     * Sets a retry policy that retries a set number of times with a constant sleep time between retries.
     */
    public ZooKeeperConfiguration setRetryNTimes(RetryNTimes retryPolicy) {
        _retryPolicy = retryPolicy;
        return this;
    }

    /**
     * Sets a retry policy that retries until a specified time has elapsed, with a constant sleep time between retries.
     */
    public ZooKeeperConfiguration setRetryUntilElapsed(RetryUntilElapsed retryPolicy) {
        _retryPolicy = retryPolicy;
        return this;
    }
}
