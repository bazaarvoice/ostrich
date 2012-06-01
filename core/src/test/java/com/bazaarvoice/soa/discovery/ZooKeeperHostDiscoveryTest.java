package com.bazaarvoice.soa.discovery;

import com.bazaarvoice.soa.HostDiscovery;
import com.bazaarvoice.soa.ServiceEndpoint;
import com.bazaarvoice.soa.registry.ZooKeeperServiceRegistry;
import com.bazaarvoice.soa.test.ZooKeeperTest;
import com.bazaarvoice.soa.zookeeper.ZooKeeperConnection;
import com.google.common.collect.Iterables;
import com.google.common.io.Closeables;
import com.netflix.curator.framework.CuratorFramework;
import org.junit.Test;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ZooKeeperHostDiscoveryTest extends ZooKeeperTest {
    private static final ServiceEndpoint FOO = new ServiceEndpoint("Foo", "server", 8080);

    private ZooKeeperServiceRegistry _registry;
    private ZooKeeperHostDiscovery _discovery;

    @Override
    public void setup() throws Exception {
        super.setup();
        _registry = new ZooKeeperServiceRegistry(newZooKeeperConnectionFactory());
        _discovery = new ZooKeeperHostDiscovery(newCurator(), FOO.getServiceName());
    }

    @Override
    public void teardown() throws Exception {
        Closeables.closeQuietly(_discovery);
        super.teardown();
    }

    @Test(expected = NullPointerException.class)
    public void testNullConfiguration() {
        new ZooKeeperHostDiscovery((ZooKeeperConnection) null, FOO.getServiceName());
    }

    @Test(expected = NullPointerException.class)
    public void testNullServiceName() throws Exception {
        new ZooKeeperHostDiscovery(newCurator(), null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testEmptyServiceName() throws Exception {
        new ZooKeeperHostDiscovery(newCurator(), "");
    }

    @Test
    public void testRegisterService() {
        _registry.register(FOO);
        assertTrue(waitUntilSize(_discovery.getHosts(), 1));
    }

    @Test
    public void testUnregisterService() {
        _registry.register(FOO);
        assertTrue(waitUntilSize(_discovery.getHosts(), 1));

        _registry.unregister(FOO);
        assertTrue(waitUntilSize(_discovery.getHosts(), 0));
    }

    @Test
    public void testClose() throws IOException {
        // After closing, HostDiscovery returns no hosts so clients won't work if they accidentally keep using it.
        _registry.register(FOO);
        assertTrue(waitUntilSize(_discovery.getHosts(), 1));
        _discovery.close();
        assertTrue(Iterables.isEmpty(_discovery.getHosts()));
        _discovery = null;
    }

    @Test
    public void testWaitForData() throws Exception {
        // Create the HostDiscovery after registration is done so there's at least one initial host
        _registry.register(FOO);
        HostDiscovery discovery = new ZooKeeperHostDiscovery(newCurator(), FOO.getServiceName());
        assertEquals(Iterables.size(discovery.getHosts()), 1);
    }

    @Test
    public void testMembershipCheck() {
        _registry.register(FOO);
        assertTrue(waitUntilSize(_discovery.getHosts(), 1));
        assertTrue(_discovery.contains(FOO));
        assertFalse(_discovery.contains(new ServiceEndpoint("Foo", "server2", 8080)));
    }

    @Test
    public void testAlreadyExistingEndpointsDoNotFireEvents() throws Exception {
        _registry.register(FOO);

        HostDiscovery discovery = new ZooKeeperHostDiscovery(newCurator(), FOO.getServiceName());
        assertEquals(Iterables.size(discovery.getHosts()), 1);

        CountingListener eventCounter = new CountingListener();
        discovery.addListener(eventCounter);

        // Don't know when the register() will take effect.  Execute and wait for an
        // unregister--that should be long enough to wait.
        _registry.unregister(FOO);
        assertTrue(waitUntilSize(discovery.getHosts(), 0));

        assertEquals(0, eventCounter.getNumAdds());  // endpoints initially visible never fire add events
    }

    @Test
    public void testServiceRemovedWhenSessionKilled() throws Exception {
        _registry.register(FOO);
        assertTrue(waitUntilSize(_discovery.getHosts(), 1));

        killSession(_discovery.getCurator());

        // The entry gets cleaned up because we've lost contact with ZooKeeper
        assertTrue(waitUntilSize(_discovery.getHosts(), 0));
    }

    @Test
    public void testServiceReRegisteredWhenSessionKilled() throws Exception {
        _registry.register(FOO);
        assertTrue(waitUntilSize(_discovery.getHosts(), 1));

        killSession(_discovery.getCurator());

        // The entry gets cleaned up because we've lost contact with ZooKeeper
        assertTrue(waitUntilSize(_discovery.getHosts(), 0));

        // Then it automatically gets created when the connection is re-established with ZooKeeper
        assertTrue(waitUntilSize(_discovery.getHosts(), 1));
    }

    @Test
    public void testRegisterServiceCallsListener() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        _discovery.addListener(new CountDownListener(latch, null));

        _registry.register(FOO);
        assertTrue(latch.await(10, TimeUnit.SECONDS));
    }

    @Test
    public void testUnregisterServiceCallsListener() throws Exception {
        CountDownLatch addLatch = new CountDownLatch(1);
        CountDownLatch removeLatch = new CountDownLatch(1);
        _discovery.addListener(new CountDownListener(addLatch, removeLatch));

        _registry.register(FOO);
        assertTrue(addLatch.await(10, TimeUnit.SECONDS));

        _registry.unregister(FOO);
        assertTrue(removeLatch.await(10, TimeUnit.SECONDS));
    }

    @Test
    public void testRemovedListenerDoesNotSeeEvents() throws Exception {
        CountDownLatch addLatch = new CountDownLatch(1);
        CountDownLatch removeLatch = new CountDownLatch(1);
        _discovery.addListener(new CountDownListener(addLatch, removeLatch));

        CountingListener eventCounter = new CountingListener();
        _discovery.addListener(eventCounter);
        _discovery.removeListener(eventCounter);

        _registry.register(FOO);
        assertTrue(addLatch.await(10, TimeUnit.SECONDS));

        _registry.unregister(FOO);
        assertTrue(removeLatch.await(10, TimeUnit.SECONDS));

        assertEquals(0, eventCounter.getNumEvents());
    }

    @Test
    public void testListenerCalledWhenSessionKilled() throws Exception {
        CountDownLatch addLatch = new CountDownLatch(1);
        CountDownLatch removeLatch = new CountDownLatch(1);
        _discovery.addListener(new CountDownListener(addLatch, removeLatch));

        _registry.register(FOO);
        assertTrue(addLatch.await(10, TimeUnit.SECONDS));

        killSession(_discovery.getCurator());

        // The entry gets cleaned up because we've lost contact with ZooKeeper
        assertTrue(removeLatch.await(10, TimeUnit.SECONDS));
    }

    @Test
    public void testListenerCalledWhenServiceIsReregisteredAfterSessionKilled() throws Exception {
        CountDownLatch addLatch = new CountDownLatch(1);
        _discovery.addListener(new CountDownListener(addLatch, null));

        _registry.register(FOO);
        assertTrue(addLatch.await(10, TimeUnit.SECONDS));

        CountDownLatch reAddLatch = new CountDownLatch(1);
        CountDownLatch removeLatch = new CountDownLatch(1);
        _discovery.addListener(new CountDownListener(reAddLatch, removeLatch));

        killSession(_discovery.getCurator());

        // The entry gets cleaned up because we've lost contact with ZooKeeper
        assertTrue(removeLatch.await(10, TimeUnit.SECONDS));

        // Then it automatically gets created when the connection is re-established with ZooKeeper
        assertTrue(reAddLatch.await(10, TimeUnit.SECONDS));
    }

    @Test
    public void testMultipleListeners() throws Exception {
        CountDownLatch addLatch = new CountDownLatch(2);
        CountDownLatch removeLatch = new CountDownLatch(2);
        _discovery.addListener(new CountDownListener(addLatch, removeLatch));
        _discovery.addListener(new CountDownListener(addLatch, removeLatch));

        _registry.register(FOO);
        assertTrue(addLatch.await(10, TimeUnit.SECONDS));

        _registry.unregister(FOO);
        assertTrue(removeLatch.await(10, TimeUnit.SECONDS));
    }

    @Test
    public void testInitializeRacesRemove() throws Exception {
        // Create a new ZK connection now so it's ready-to-go when we need it.
        CuratorFramework curator = newCurator();

        // Register FOO and wait until it's visible.
        _registry.register(FOO);
        assertTrue(waitUntilSize(_discovery.getHosts(), 1));

        // Unregister FOO and create a new HostDiscovery instance as close together as we can, so they race.
        _registry.unregister(FOO);
        HostDiscovery discovery = new ZooKeeperHostDiscovery(curator, FOO.getServiceName());
        assertTrue(waitUntilSize(discovery.getHosts(), 0));
    }

    private static <T> boolean waitUntilSize(Iterable<T> iterable, int size, long timeout, TimeUnit unit) {
        long start = System.nanoTime();
        while (System.nanoTime() - start <= unit.toNanos(timeout)) {
            if (Iterables.size(iterable) == size) {
                return true;
            }

            Thread.yield();
        }

        return false;
    }

    private static <T> boolean waitUntilSize(Iterable<T> iterable, int size) {
        return waitUntilSize(iterable, size, 10, TimeUnit.SECONDS);
    }

    private static final class CountDownListener implements HostDiscovery.EndpointListener {
        private final CountDownLatch _addLatch;
        private final CountDownLatch _removeLatch;

        public CountDownListener(CountDownLatch addLatch, CountDownLatch removeLatch) {
            _addLatch = addLatch;
            _removeLatch = removeLatch;
        }

        @Override
        public void onEndpointAdded(ServiceEndpoint endpoint) {
            if (_addLatch != null) {
                _addLatch.countDown();
            }
        }

        @Override
        public void onEndpointRemoved(ServiceEndpoint endpoint) {
            if (_removeLatch != null) {
                _removeLatch.countDown();
            }
        }
    }

    private static final class CountingListener implements HostDiscovery.EndpointListener {
        private int _numAdds;
        private int _numRemoves;

        @Override
        public void onEndpointAdded(ServiceEndpoint endpoint) {
            _numAdds++;
        }

        @Override
        public void onEndpointRemoved(ServiceEndpoint endpoint) {
            _numRemoves++;
        }

        public int getNumAdds() {
            return _numAdds;
        }

        public int getNumRemoves() {
            return _numRemoves;
        }

        public int getNumEvents() {
            return _numAdds + _numRemoves;
        }
    }
}
