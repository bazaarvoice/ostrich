package com.bazaarvoice.ostrich.registry.zookeeper;

import com.bazaarvoice.curator.recipes.PersistentEphemeralNode;
import com.bazaarvoice.ostrich.ServiceEndPoint;
import com.bazaarvoice.ostrich.ServiceEndPointBuilder;
import com.bazaarvoice.ostrich.ServiceEndPointJsonCodec;
import com.google.common.base.Charsets;
import com.google.common.base.Strings;
import com.netflix.curator.framework.CuratorFramework;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.concurrent.TimeUnit;

import static com.bazaarvoice.ostrich.registry.zookeeper.ZooKeeperServiceRegistry.MAX_DATA_SIZE;
import static com.bazaarvoice.ostrich.registry.zookeeper.ZooKeeperServiceRegistry.makeEndPointPath;
import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ZooKeeperServiceRegistryTest {
    private static final ServiceEndPoint FOO = newEndPoint("Foo", "server:80", "");
    private static final String FOO_PATH = makeEndPointPath(FOO);

    private ZooKeeperServiceRegistry.NodeFactory _nodeFactory;
    private ZooKeeperServiceRegistry _registry;

    @Before
    public void setup() {
        _nodeFactory = mock(ZooKeeperServiceRegistry.NodeFactory.class);
        when(_nodeFactory.create(anyString(), any(byte[].class)))
                .thenAnswer(new Answer<PersistentEphemeralNode>() {
                    @Override
                    public PersistentEphemeralNode answer(InvocationOnMock invocation) throws Throwable {
                        return mock(PersistentEphemeralNode.class);
                    }
                });
        _registry = new ZooKeeperServiceRegistry(_nodeFactory);
    }

    @After
    public void teardown() throws Exception {
        _registry.close();
    }

    @Test(expected = NullPointerException.class)
    public void testNullCurator() throws Exception {
        new ZooKeeperServiceRegistry((CuratorFramework) null);
    }

    @Test(expected = NullPointerException.class)
    public void testNullFactory() {
        new ZooKeeperServiceRegistry((ZooKeeperServiceRegistry.NodeFactory) null);
    }

    @Test
    public void testConstructor() {
        new ZooKeeperServiceRegistry(mock(CuratorFramework.class));
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
        _registry.register(newEndPoint(FOO.getServiceName(), FOO.getId(), ""), false);
    }

    @Test
    public void testRegister() throws Exception {
        PersistentEphemeralNode node = mock(PersistentEphemeralNode.class);
        when(_nodeFactory.create(anyString(), any(byte[].class))).thenReturn(node);

        _registry.register(FOO);

        ArgumentCaptor<byte[]> dataCaptor = ArgumentCaptor.forClass(byte[].class);

        verify(_nodeFactory).create(eq(FOO_PATH), dataCaptor.capture());
        assertEquals(FOO, ServiceEndPointJsonCodec.fromJson(new String(dataCaptor.getValue())));
        verify(node, never()).close(anyLong(), any(TimeUnit.class));
    }

    @Test
    public void testDuplicateRegister() throws Exception {
        PersistentEphemeralNode firstNode = mock(PersistentEphemeralNode.class);
        PersistentEphemeralNode secondNode = mock(PersistentEphemeralNode.class);
        when(_nodeFactory.create(anyString(), any(byte[].class))).thenReturn(firstNode, secondNode);

        _registry.register(FOO);
        _registry.register(FOO);

        verify(_nodeFactory, times(2)).create(eq(FOO_PATH), any(byte[].class));
        verify(firstNode).close(anyLong(), any(TimeUnit.class));
    }

    @Test
    public void testUnregister() throws Exception {
        PersistentEphemeralNode node = mock(PersistentEphemeralNode.class);
        when(_nodeFactory.create(anyString(), any(byte[].class))).thenReturn(node);

        _registry.register(FOO);

        _registry.unregister(FOO);

        verify(node).close(anyLong(), any(TimeUnit.class));
    }

    @Test
    public void testUnregisterWithoutFirstRegistering() throws Exception {
        _registry.unregister(FOO);

        verify(_nodeFactory, never()).create(eq(FOO_PATH), any(byte[].class));
    }

    @Test
    public void testDuplicateUnregister() throws Exception {
        _registry.register(FOO);

        _registry.unregister(FOO);
        _registry.unregister(FOO);
    }

    @Test
    public void testServiceNodeIsDeletedWhenRegistryIsClosed() throws Exception {
        PersistentEphemeralNode node = mock(PersistentEphemeralNode.class);
        when(_nodeFactory.create(anyString(), any(byte[].class))).thenReturn(node);

        _registry.register(FOO);
        _registry.close();

        verify(node).close(anyLong(), any(TimeUnit.class));
    }

    private static ServiceEndPoint newEndPoint(String serviceName, String id, String payload) {
        return new ServiceEndPointBuilder()
                .withServiceName(serviceName)
                .withId(id)
                .withPayload(payload)
                .build();
    }
}
