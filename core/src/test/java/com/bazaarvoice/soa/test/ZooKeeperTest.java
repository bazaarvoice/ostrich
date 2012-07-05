package com.bazaarvoice.soa.test;

import com.bazaarvoice.soa.HostDiscovery;
import com.bazaarvoice.soa.ServiceEndPoint;
import com.bazaarvoice.soa.zookeeper.ZooKeeperConfiguration;
import com.bazaarvoice.soa.zookeeper.ZooKeeperConnection;
import com.google.common.base.Throwables;
import com.google.common.collect.Lists;
import com.google.common.io.Closeables;
import com.netflix.curator.framework.CuratorFramework;
import com.netflix.curator.framework.CuratorFrameworkFactory;
import com.netflix.curator.retry.RetryNTimes;
import com.netflix.curator.test.KillSession;
import com.netflix.curator.test.TestingServer;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.junit.After;
import org.junit.Before;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertNotNull;

public abstract class ZooKeeperTest {
    protected TestingServer _zooKeeperServer;

    /** All of the curator instances that we've created running the test. */
    private List<CuratorFramework> _curatorInstances = Lists.newArrayList();

    /** All of the connection instances that we've created running the test. */
    private List<ZooKeeperConnection> _connections = Lists.newArrayList();

    @Before
    public void setup() throws Exception {
        _zooKeeperServer = new TestingServer();
    }

    @After
    public void teardown() throws Exception {
        for (ZooKeeperConnection connection : _connections) {
            Closeables.closeQuietly(connection);
        }
        for (CuratorFramework curator : _curatorInstances) {
            Closeables.closeQuietly(curator);
        }

        Closeables.closeQuietly(_zooKeeperServer);
    }

    public ZooKeeperConnection newZooKeeperConnection() throws Exception {
        // For test case purposes don't retry at all.  This should never be done in production!!!
        return newZooKeeperConnection(new ZooKeeperConfiguration().withBoundedExponentialBackoffRetry(100, 1000, 1));
    }

    public ZooKeeperConnection newZooKeeperConnection(ZooKeeperConfiguration configuration) {
        assertNotNull("ZooKeeper testing server is null, did you forget to call super.setup()", _zooKeeperServer);

        ZooKeeperConnection connection = configuration
                .withConnectString(_zooKeeperServer.getConnectString())
                .connect();

        _connections.add(connection);

        return connection;
    }

    public CuratorFramework newCurator() throws Exception {
        return newCurator(CuratorFrameworkFactory.builder().retryPolicy(new RetryNTimes(0, 0)));
    }

    public CuratorFramework newCurator(CuratorFrameworkFactory.Builder builder) throws Exception {
        assertNotNull("ZooKeeper testing server is null, did you forget to call super.setup()", _zooKeeperServer);

        CuratorFramework curator = builder
                .connectString(_zooKeeperServer.getConnectString())
                .build();
        curator.start();

        _curatorInstances.add(curator);

        return curator;
    }

    public void killSession(CuratorFramework curator) throws Exception {
        KillSession.kill(curator.getZookeeperClient().getZooKeeper(), _zooKeeperServer.getConnectString());
    }

    protected static class Trigger implements Watcher, HostDiscovery.EndpointListener {
        private final CountDownLatch _latch;

        public Trigger() {
            _latch = new CountDownLatch(1);
        }

        @Override
        public void process(WatchedEvent event) {
            _latch.countDown();
        }

        @Override
        public void onEndpointAdded(ServiceEndPoint endpoint) {
            _latch.countDown();
        }

        @Override
        public void onEndpointRemoved(ServiceEndPoint endpoint) {
            _latch.countDown();
        }

        public boolean firedWithin(long duration, TimeUnit unit) {
            try {
                return _latch.await(duration, unit);
            } catch (InterruptedException e) {
                throw Throwables.propagate(e);
            }
        }
    }
}
