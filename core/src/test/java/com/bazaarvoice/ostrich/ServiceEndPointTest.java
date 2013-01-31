package com.bazaarvoice.ostrich;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class ServiceEndPointTest {
    @Test
    public void testEqualsSame() {
        ServiceEndPoint endPoint = endPoint("Foo", "server:80");
        assertEquals(endPoint, endPoint);
    }

    @Test
    public void testEqualsEquivalent() {
        ServiceEndPoint endPoint1 = endPoint("Foo", "server:80");
        ServiceEndPoint endPoint2 = endPoint("Foo", "server:80");
        assertEquals(endPoint1, endPoint2);
    }

    @Test
    public void testEqualsNull() {
        ServiceEndPoint endPoint = endPoint("Foo", "server:80");
        assertNotEquals(endPoint, null);
    }

    @Test
    public void testEqualsServiceName() {
        ServiceEndPoint endPoint1 = endPoint("Foo", "server:80");
        ServiceEndPoint endPoint2 = endPoint("Bar", "server:80");
        assertNotEquals(endPoint1, endPoint2);
    }

    @Test
    public void testEqualsId() {
        ServiceEndPoint endPoint1 = endPoint("Foo", "server:80");
        ServiceEndPoint endPoint2 = endPoint("Foo", "server:81");
        assertNotEquals(endPoint1, endPoint2);
    }

    @Test
    public void testEqualsEmptyPayload() {
        ServiceEndPoint endPoint1 = endPoint("Foo", "server:80");
        ServiceEndPoint endPoint2 = endPoint("Foo", "server:80", "");
        assertNotEquals(endPoint1, endPoint2);
    }

    @Test
    public void testEqualsNonEmptyPayload() {
        ServiceEndPoint endPoint1 = endPoint("Foo", "server:80");
        ServiceEndPoint endPoint2 = endPoint("Foo", "server:80", "payload");
        assertNotEquals(endPoint1, endPoint2);
    }

    @Test
    public void testHashCodeEquals() {
        ServiceEndPoint endPoint1 = endPoint("Foo", "server:80");
        ServiceEndPoint endPoint2 = endPoint("Foo", "server:80");
        assertEquals(endPoint1.hashCode(), endPoint2.hashCode());
    }

    private ServiceEndPoint endPoint(String serviceName, String id) {
        return endPoint(serviceName, id, null);
    }

    private ServiceEndPoint endPoint(String serviceName, String id, String payload) {
        return new ServiceEndPointBuilder()
                .withServiceName(serviceName)
                .withId(id)
                .withPayload(payload)
                .build();
    }

    private void assertNotEquals(Object a, Object b) {
        assertFalse(a.equals(b));
    }
}
