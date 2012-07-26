package com.bazaarvoice.soa;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

public class ServiceEndPointBuilderTest {
    @Test(expected = IllegalStateException.class)
    public void testMissingServiceName() {
        new ServiceEndPointBuilder()
                .withId("id")
                .build();
    }

    @Test(expected = IllegalStateException.class)
    public void testMissingId() {
        new ServiceEndPointBuilder()
                .withServiceName("service")
                .build();
    }

    @Test
    public void testServiceName() {
        ServiceEndPoint endPoint = new ServiceEndPointBuilder()
                .withServiceName("service")
                .withId("id")
                .build();
        assertEquals("service", endPoint.getServiceName());
    }

    @Test
    public void testId() {
        ServiceEndPoint endPoint = new ServiceEndPointBuilder()
                .withServiceName("service")
                .withId("id")
                .build();
        assertEquals("id", endPoint.getId());
    }

    @Test
    public void testNoPayload() {
        ServiceEndPoint endPoint = new ServiceEndPointBuilder()
                .withServiceName("service")
                .withId("id")
                .build();
        assertNull(endPoint.getPayload());
    }

    @Test
    public void testEmptyPayload() {
        ServiceEndPoint endPoint = new ServiceEndPointBuilder()
                .withServiceName("service")
                .withId("id")
                .withPayload("")
                .build();
        assertEquals("", endPoint.getPayload());
    }

    @Test
    public void testPayload() {
        ServiceEndPoint endPoint = new ServiceEndPointBuilder()
                .withServiceName("service")
                .withId("id")
                .withPayload("payload")
                .build();
        assertEquals("payload", endPoint.getPayload());
    }

    @Test
    public void testInvalidServiceNames() {
        String[] invalidNames = new String[] {"Foo$Bar", "%", "a@b", "!", null, ""};

        for (String name : invalidNames) {
            try {
                new ServiceEndPointBuilder().withServiceName(name);
                fail(name + " was allowed");
            } catch (AssertionError e) {
                throw e;
            } catch (IllegalArgumentException e) {
                // Expected
            } catch (Throwable t) {
                fail(name + " threw " + t.getMessage());
            }
        }
    }

    @Test
    public void testInvalidIds() {
        String[] invalidIds = new String[] {"Foo$Bar", "%", "a@b", "!", null, ""};

        for (String id : invalidIds) {
            try {
                new ServiceEndPointBuilder().withId(id);
                fail(id + " was allowed");
            } catch (AssertionError e) {
                throw e;
            } catch (IllegalArgumentException e) {
                // Expected
            } catch (Throwable t) {
                fail(id + " threw " + t.getMessage());
            }
        }
    }
}
