package com.bazaarvoice.soa.registry;

import com.bazaarvoice.soa.ServiceEndPoint;
import com.bazaarvoice.soa.ServiceEndPointBuilder;
import com.bazaarvoice.soa.ServiceEndPointJsonCodec;
import com.bazaarvoice.zookeeper.ZooKeeperConfiguration;
import com.bazaarvoice.zookeeper.ZooKeeperConnection;
import com.bazaarvoice.soa.test.ZooKeeperTest;
import com.google.common.base.Charsets;
import com.google.common.base.Strings;
import com.netflix.curator.framework.CuratorFramework;
import org.apache.zookeeper.data.Stat;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

import static com.bazaarvoice.soa.registry.ZooKeeperServiceRegistry.MAX_DATA_SIZE;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertNotNull;

public class ZooKeeperServiceRegistryTest extends ZooKeeperTest {
    private static final ServiceEndPoint FOO = newEndPoint("Foo", "server:80", "");

    private ZooKeeperServiceRegistry _registry;

    @Before
    public void setup() throws Exception {
        super.setup();
        _registry = new ZooKeeperServiceRegistry(newMockZooKeeperConnection());
    }

    @After
    public void teardown() throws Exception {
        _registry.close();
        super.teardown();
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
    public void testRegisterAfterClose() throws Exception {
        _registry.close();
        _registry.register(FOO);
    }

    @Test(expected = IllegalStateException.class)
    public void testUnregisterAfterClose() throws Exception {
        _registry.close();
        _registry.unregister(FOO);
    }

    @Test(expected = IllegalStateException.class)
    public void testLargePayloadSize() {
        int padding = ServiceEndPointJsonCodec.toJson(FOO).getBytes(Charsets.UTF_8).length;
        String payload = Strings.repeat("x", MAX_DATA_SIZE - padding);
        _registry.register(newEndPoint(FOO.getServiceName(), FOO.getId(), payload), false);
    }

    @Test
    public void testMediumPayloadSize() {
        int padding = ServiceEndPointJsonCodec.toJson(FOO).getBytes(Charsets.UTF_8).length;
        String payload = Strings.repeat("x", MAX_DATA_SIZE - padding - 1);
        _registry.register(newEndPoint(FOO.getServiceName(), FOO.getId(), payload), false);
    }

    @Test
    public void testEmptyPayload() {
        _registry.register(newEndPoint("Foo", "server:80", ""), false);
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
        String path = _registry.getRegisteredEndPointPath(FOO);

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
        String path = _registry.getRegisteredEndPointPath(FOO);

        _registry.unregister(FOO);
        _registry.unregister(FOO);
        assertNodeDoesNotExist(path, curator);
    }

    @Test
    public void testServiceNodeIsDeletedWhenRegistryIsClosed() throws Exception {
        CuratorFramework curator = newCurator();

        _registry.register(FOO);
        String path = _registry.getRegisteredEndPointPath(FOO);

        Trigger deletionTrigger = new Trigger();
        curator.checkExists().usingWatcher(deletionTrigger).forPath(path);

        _registry.close();

        assertTrue(deletionTrigger.firedWithin(10, TimeUnit.SECONDS));
    }

    @Test
    public void testServiceNodeIsDeletedWhenSessionDisconnects() throws Exception {
        CuratorFramework curator = newCurator();

        _registry.register(FOO);
        String path = _registry.getRegisteredEndPointPath(FOO);

        Trigger deletionTrigger = new Trigger();
        curator.checkExists().usingWatcher(deletionTrigger).forPath(path);

        // Kill the registry's ZooKeeper session.  That should force the ephemeral node that it created to be
        // automatically cleaned up.
        killSession(_registry.getCurator());

        // Wait for the trigger to be called up to 10 seconds.  This should be plenty of time for the node to be
        // removed, if it's not called by then, fail the test.
        assertTrue(deletionTrigger.firedWithin(10, TimeUnit.SECONDS));
    }

    @Test
    public void testServiceNodeIsRecreatedWhenSessionReconnects() throws Exception {
        CuratorFramework curator = newCurator();

        _registry.register(FOO);
        String path = _registry.getRegisteredEndPointPath(FOO);

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
        String path = _registry.getRegisteredEndPointPath(FOO);

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
        ZooKeeperConnection connection = newZooKeeperConnection(new ZooKeeperConfiguration().withNamespace("/dc1"));
        ZooKeeperServiceRegistry registry = new ZooKeeperServiceRegistry(connection);
        registry.register(FOO);

        // Use a non-namespaced curator to check that the path was created in the correct namespace
        assertNotNull(newCurator().checkExists().forPath("/dc1" + registry.getRegisteredEndPointPath(FOO)));
    }

    @Test
    public void testEmptyNamespace() throws Exception {
        ZooKeeperConnection connection = newZooKeeperConnection(new ZooKeeperConfiguration().withNamespace(""));
        ZooKeeperServiceRegistry registry = new ZooKeeperServiceRegistry(connection);
        registry.register(FOO);

        // Use a non-namespaced curator to check that the path was created in the correct namespace
        assertNotNull(newCurator().checkExists().forPath(registry.getRegisteredEndPointPath(FOO)));
    }

    private void assertRegistered(ServiceEndPoint endPoint, CuratorFramework curator) throws Exception {
        String path = _registry.getRegisteredEndPointPath(endPoint);
        assertNotNull(curator.checkExists().forPath(path));
    }

    private void assertNodeDoesNotExist(String path, CuratorFramework curator) throws Exception {
        assertNull(curator.checkExists().forPath(path));
    }

    private static ServiceEndPoint newEndPoint(String serviceName, String id, String payload) {
        return new ServiceEndPointBuilder()
                .withServiceName(serviceName)
                .withId(id)
                .withPayload(payload)
                .build();
    }
}
