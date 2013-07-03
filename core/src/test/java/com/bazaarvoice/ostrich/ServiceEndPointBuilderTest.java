/*
 * Copyright 2013 Bazaarvoice, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.bazaarvoice.ostrich;

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
