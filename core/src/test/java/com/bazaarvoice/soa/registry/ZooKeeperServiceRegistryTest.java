package com.bazaarvoice.soa.registry;

import com.bazaarvoice.soa.ServiceEndpoint;
import com.bazaarvoice.soa.test.ZooKeeperTest;
import com.bazaarvoice.soa.zookeeper.ZooKeeperConfiguration;
import com.bazaarvoice.soa.zookeeper.ZooKeeperConnection;
import com.google.common.base.Charsets;
import com.google.common.base.Strings;
import com.netflix.curator.framework.CuratorFramework;
import org.apache.zookeeper.data.Stat;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

import static com.bazaarvoice.soa.registry.ZooKeeperServiceRegistry.MAX_DATA_SIZE;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ZooKeeperServiceRegistryTest extends ZooKeeperTest {
    private static final ServiceEndpoint FOO = new ServiceEndpoint("Foo", "server", 8080);

    private ZooKeeperServiceRegistry _registry;

    @Before
    public void setup() throws Exception {
        super.setup();
        _registry = new ZooKeeperServiceRegistry(newCurator());
    }

    @Test(expected = NullPointerException.class)
    public void testNullConnection() throws Exception {
        new ZooKeeperServiceRegistry((ZooKeeperConnection) null);
    }

    @Test(expected = NullPointerException.class)
    public void testRegisterNullService() throws Exception {
        _registry.register(null);
    }

    @Test(expected = NullPointerException.class)
    public void testUnregisterNullService() throws Exception {
        _registry.unregister(null);
    }

    @Test(expected = IllegalStateException.class)
    public void testLargePayloadSize() {
        int padding = new ServiceEndpoint("Foo", "server", 80, "").toJson().getBytes(Charsets.UTF_8).length;
        _registry.register(new ServiceEndpoint("Foo", "server", 80, Strings.repeat("x", MAX_DATA_SIZE - padding)));
    }

    @Test
    public void testMediumPayloadSize() {
        int padding = new ServiceEndpoint("Foo", "server", 80, "").toJson().getBytes(Charsets.UTF_8).length;
        _registry.register(new ServiceEndpoint("Foo", "server", 80, Strings.repeat("x", MAX_DATA_SIZE - padding - 1)));
    }

    @Test
    public void testEmptyPayload() {
        _registry.register(new ServiceEndpoint("Foo", "server", 80, ""));
    }

    @Test
    public void testRegister() throws Exception {
        CuratorFramework curator = newCurator();

        _registry.register(FOO);
        assertRegistered(FOO, curator);
    }

    @Test
    public void testDuplicateRegister() throws Exception {
        CuratorFramework curator = newCurator();

        _registry.register(FOO);
        _registry.register(FOO);
        assertRegistered(FOO, curator);
    }

    @Test
    public void testUnregister() throws Exception {
        CuratorFramework curator = newCurator();

        _registry.register(FOO);
        String path = _registry.getRegisteredEndpointPath(FOO);

        _registry.unregister(FOO);
        assertNodeDoesNotExist(path, curator);
    }

    @Test
    public void testUnregisterWithoutFirstRegistering() throws Exception {
        _registry.unregister(FOO);
    }

    @Test
    public void testDuplicateUnregister() throws Exception {
        CuratorFramework curator = newCurator();

        _registry.register(FOO);
        String path = _registry.getRegisteredEndpointPath(FOO);

        _registry.unregister(FOO);
        _registry.unregister(FOO);
        assertNodeDoesNotExist(path, curator);
    }

    @Test
    public void testServiceNodeIsDeletedWhenSessionDisconnects() throws Exception {
        CuratorFramework curator = newCurator();

        _registry.register(FOO);
        String path = _registry.getRegisteredEndpointPath(FOO);

        Trigger deletionTrigger = new Trigger();
        curator.checkExists().usingWatcher(deletionTrigger).forPath(path);

        // Kill the registry's ZooKeeper session.  That should force the ephemeral node that it created to be
        // automatically cleaned up.
        killSession(_registry.getCurator());

        // Wait for the latch to be called up to 10 seconds.  This should be plenty of time for the node to be removed,
        // if it's not called by then, fail the test.
        assertTrue(deletionTrigger.firedWithin(10, TimeUnit.SECONDS));
    }

    @Test
    public void testServiceNodeIsRecreatedWhenSessionReconnects() throws Exception {
        CuratorFramework curator = newCurator();

        _registry.register(FOO);
        String path = _registry.getRegisteredEndpointPath(FOO);

        Trigger deletionTrigger = new Trigger();
        curator.checkExists().usingWatcher(deletionTrigger).forPath(path);

        // Kill the registry's session, thus cleaning up the node...
        killSession(_registry.getCurator());

        // Make sure the node ended up getting deleted...
        assertTrue(deletionTrigger.firedWithin(10, TimeUnit.SECONDS));

        // Now put a watch in the background looking to see if it gets created...
        Trigger creationTrigger = new Trigger();
        Stat stat = curator.checkExists().usingWatcher(creationTrigger).forPath(path);
        assertTrue(stat != null || creationTrigger.firedWithin(10, TimeUnit.SECONDS));
    }

    @Test
    public void testServiceNodeIsRecreatedWhenSessionReconnectsMultipleTimes() throws Exception {
        CuratorFramework curator = newCurator();

        _registry.register(FOO);
        String path = _registry.getRegisteredEndpointPath(FOO);

        // We should be able to disconnect multiple times and each time the registry should re-create the node.
        for (int i = 0; i < 5; i++) {
            Trigger deletionTrigger = new Trigger();
            curator.checkExists().usingWatcher(deletionTrigger).forPath(path);

            // Kill the registry's session, thus cleaning up the node...
            killSession(_registry.getCurator());

            // Make sure the node ended up getting deleted...
            assertTrue(deletionTrigger.firedWithin(10, TimeUnit.SECONDS));

            // Now put a watch in the background looking to see if it gets created...
            Trigger creationTrigger = new Trigger();
            Stat stat = curator.checkExists().usingWatcher(creationTrigger).forPath(path);
            assertTrue(stat != null || creationTrigger.firedWithin(10, TimeUnit.SECONDS));
        }
    }

    @Test
    public void testNamespace() throws Exception {
        ZooKeeperConnection connection = newZooKeeperConnection(new ZooKeeperConfiguration().setNamespace("/datacenter1"));
        ZooKeeperServiceRegistry registry = new ZooKeeperServiceRegistry(connection);
        registry.register(FOO);

        // Use a non-namespaced curator to check that the path was created in the correct namespace
        assertNotNull(newCurator().checkExists().forPath("/datacenter1" + registry.getRegisteredEndpointPath(FOO)));
    }

    @Test
    public void testEmptyNamespace() throws Exception {
        ZooKeeperConnection connection = newZooKeeperConnection(new ZooKeeperConfiguration().setNamespace(""));
        ZooKeeperServiceRegistry registry = new ZooKeeperServiceRegistry(connection);
        registry.register(FOO);

        // Use a non-namespaced curator to check that the path was created in the correct namespace
        assertNotNull(newCurator().checkExists().forPath(registry.getRegisteredEndpointPath(FOO)));
    }

    private void assertRegistered(ServiceEndpoint endpoint, CuratorFramework curator) throws Exception {
        String path = _registry.getRegisteredEndpointPath(endpoint);
        assertNotNull(curator.checkExists().forPath(path));
    }

    private void assertNodeDoesNotExist(String path, CuratorFramework curator) throws Exception {
        assertNull(curator.checkExists().forPath(path));
    }
}
