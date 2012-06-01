package com.bazaarvoice.soa.test;

import com.bazaarvoice.soa.zookeeper.ZooKeeperConfiguration;
import com.bazaarvoice.soa.zookeeper.ZooKeeperConnection;
import com.google.common.collect.Lists;
import com.google.common.io.Closeables;
import com.netflix.curator.framework.CuratorFramework;
import com.netflix.curator.framework.CuratorFrameworkFactory;
import com.netflix.curator.retry.RetryNTimes;
import com.netflix.curator.test.KillSession;
import com.netflix.curator.test.TestingServer;
import org.junit.After;
import org.junit.Before;

import java.util.List;

import static org.junit.Assert.assertNotNull;

public abstract class ZooKeeperTest {
    protected TestingServer _zooKeeperServer;

    /** All of the curator instances that we've created running the test. */
    private List<CuratorFramework> _curatorInstances = Lists.newArrayList();

    @Before
    public void setup() throws Exception {
        _zooKeeperServer = new TestingServer();
    }

    @After
    public void teardown() throws Exception {
        for (CuratorFramework curator : _curatorInstances) {
            Closeables.closeQuietly(curator);
        }

        Closeables.closeQuietly(_zooKeeperServer);
    }

    public ZooKeeperConnection newZooKeeperConnectionFactory() throws Exception {
        assertNotNull("ZooKeeper testing server is null, did you forget to call super.setup()", _zooKeeperServer);

        return new ZooKeeperConfiguration()
                .setConnectString(_zooKeeperServer.getConnectString())
                .setRetryNTimes(new com.bazaarvoice.soa.zookeeper.RetryNTimes(0, 0))
                .connect();
    }

    public CuratorFramework newCurator() throws Exception {
        return newCurator(new RetryNTimes(0, 0));
    }

    private CuratorFramework newCurator(com.netflix.curator.RetryPolicy retryPolicy) throws Exception {
        assertNotNull("ZooKeeper testing server is null, did you forget to call super.setup()", _zooKeeperServer);

        CuratorFramework curator = CuratorFrameworkFactory.builder()
                .connectString(_zooKeeperServer.getConnectString())
                .retryPolicy(retryPolicy)
                .build();
        curator.start();

        _curatorInstances.add(curator);

        return curator;
    }

    public void killSession(CuratorFramework curator) throws Exception {
        KillSession.kill(curator.getZookeeperClient().getZooKeeper(), _zooKeeperServer.getConnectString());
    }
}
