package com.bazaarvoice.soa.discovery;

import com.bazaarvoice.soa.HostDiscovery;
import com.bazaarvoice.soa.ServiceEndPoint;
import com.bazaarvoice.soa.ServiceEndPointBuilder;
import com.bazaarvoice.soa.registry.ZooKeeperServiceRegistry;
import com.bazaarvoice.zookeeper.ZooKeeperConnection;
import com.bazaarvoice.zookeeper.test.ZooKeeperTest;
import com.google.common.collect.Iterables;
import com.google.common.io.Closeables;
import com.netflix.curator.framework.CuratorFramework;
import org.junit.Test;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ZooKeeperHostDiscoveryTest extends ZooKeeperTest {
    private static final ServiceEndPoint FOO = new ServiceEndPointBuilder()
            .withServiceName("Foo")
            .withId("server:8080")
            .build();

    private static final ServiceEndPoint BAR = new ServiceEndPointBuilder()
            .withServiceName("Bar")
            .withId("server:8081")
            .build();

    private ZooKeeperServiceRegistry _registry;
    private ZooKeeperHostDiscovery _discovery;

    @Override
    public void setup() throws Exception {
        super.setup();
        _registry = new ZooKeeperServiceRegistry(newZooKeeperConnection());
        _discovery = new ZooKeeperHostDiscovery(newCurator(), FOO.getServiceName());
    }

    @Override
    public void teardown() throws Exception {
        Closeables.closeQuietly(_discovery);
        Closeables.closeQuietly(_registry);
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
        _registry.register(BAR);
        assertTrue(waitUntilSize(_discovery.getHosts(), 1));
        assertTrue(_discovery.contains(FOO));
        assertFalse(_discovery.contains(BAR));
    }

    @Test
    public void testAlreadyExistingEndPointsDoNotFireEvents() throws Exception {
        _registry.register(FOO);

        HostDiscovery discovery = new ZooKeeperHostDiscovery(newCurator(), FOO.getServiceName());
        assertEquals(Iterables.size(discovery.getHosts()), 1);

        CountingListener eventCounter = new CountingListener();
        discovery.addListener(eventCounter);

        // Don't know when the register() will take effect.  Execute and wait for an
        // unregister--that should be long enough to wait.
        _registry.unregister(FOO);
        assertTrue(waitUntilSize(discovery.getHosts(), 0));

        assertEquals(0, eventCounter.getNumAdds());  // endPoints initially visible never fire add events
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
        EndPointTrigger trigger = new EndPointTrigger();
        _discovery.addListener(trigger);

        _registry.register(FOO);
        assertTrue(trigger.addedWithin(10, TimeUnit.SECONDS));
    }

    @Test
    public void testUnregisterServiceCallsListener() throws Exception {
        EndPointTrigger trigger = new EndPointTrigger();
        _discovery.addListener(trigger);

        _registry.register(FOO);
        assertTrue(trigger.addedWithin(10, TimeUnit.SECONDS));

        _registry.unregister(FOO);
        assertTrue(trigger.removedWithin(10, TimeUnit.SECONDS));
    }

    @Test
    public void testRemovedListenerDoesNotSeeEvents() throws Exception {
        EndPointTrigger trigger = new EndPointTrigger();
        _discovery.addListener(trigger);

        CountingListener eventCounter = new CountingListener();
        _discovery.addListener(eventCounter);
        _discovery.removeListener(eventCounter);

        _registry.register(FOO);
        assertTrue(trigger.addedWithin(10, TimeUnit.SECONDS));

        _registry.unregister(FOO);
        assertTrue(trigger.removedWithin(10, TimeUnit.SECONDS));

        assertEquals(0, eventCounter.getNumEvents());
    }

    @Test
    public void testListenerCalledWhenSessionKilled() throws Exception {
        EndPointTrigger trigger = new EndPointTrigger();
        _discovery.addListener(trigger);

        _registry.register(FOO);
        assertTrue(trigger.addedWithin(10, TimeUnit.SECONDS));

        killSession(_discovery.getCurator());

        // The entry gets cleaned up because we've lost contact with ZooKeeper
        assertTrue(trigger.removedWithin(10, TimeUnit.SECONDS));
    }

    @Test
    public void testListenerCalledWhenServiceIsReregisteredAfterSessionKilled() throws Exception {
        EndPointTrigger initialTrigger = new EndPointTrigger();
        _discovery.addListener(initialTrigger);

        _registry.register(FOO);
        assertTrue(initialTrigger.addedWithin(10, TimeUnit.SECONDS));

        EndPointTrigger trigger = new EndPointTrigger();
        _discovery.addListener(trigger);

        killSession(_discovery.getCurator());

        // The entry gets cleaned up because we've lost contact with ZooKeeper
        assertTrue(trigger.removedWithin(10, TimeUnit.SECONDS));

        // Then it automatically gets created when the connection is re-established with ZooKeeper
        assertTrue(trigger.addedWithin(10, TimeUnit.SECONDS));
    }

    @Test
    public void testMultipleListeners() throws Exception {
        EndPointTrigger trigger1 = new EndPointTrigger();
        EndPointTrigger trigger2 = new EndPointTrigger();
        _discovery.addListener(trigger1);
        _discovery.addListener(trigger2);

        _registry.register(FOO);
        assertTrue(trigger1.addedWithin(10, TimeUnit.SECONDS));
        assertTrue(trigger2.addedWithin(10, TimeUnit.SECONDS));

        _registry.unregister(FOO);
        assertTrue(trigger1.removedWithin(10, TimeUnit.SECONDS));
        assertTrue(trigger2.removedWithin(10, TimeUnit.SECONDS));
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

    private static final class EndPointTrigger implements HostDiscovery.EndPointListener {
        private final Trigger _addTrigger = new Trigger();
        private final Trigger _removeTrigger = new Trigger();

        @Override
        public void onEndPointAdded(ServiceEndPoint endPoint) {
            _addTrigger.fire();
        }

        @Override
        public void onEndPointRemoved(ServiceEndPoint endPoint) {
            _removeTrigger.fire();
        }

        public boolean addedWithin(long duration, TimeUnit unit) throws InterruptedException {
            return _addTrigger.firedWithin(duration, unit);
        }

        public boolean removedWithin(long duration, TimeUnit unit) throws InterruptedException {
            return _removeTrigger.firedWithin(duration, unit);
        }
    }

    private static final class CountingListener implements HostDiscovery.EndPointListener {
        private int _numAdds;
        private int _numRemoves;

        @Override
        public void onEndPointAdded(ServiceEndPoint endPoint) {
            _numAdds++;
        }

        @Override
        public void onEndPointRemoved(ServiceEndPoint endPoint) {
            _numRemoves++;
        }

        public int getNumAdds() {
            return _numAdds;
        }

        public int getNumEvents() {
            return _numAdds + _numRemoves;
        }
    }
}
