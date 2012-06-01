package com.bazaarvoice.soa;

import org.codehaus.jackson.JsonFactory;
import org.codehaus.jackson.JsonGenerator;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormatter;
import org.junit.Test;

import java.io.IOException;
import java.io.StringWriter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

public class ServiceEndpointTest
{
    private static final DateTimeFormatter ISO8601 = ServiceEndpoint.ISO8601;

    @Test
    public void testInvalidServiceNames() {
        ServiceEndpointBuilder base = new ServiceEndpointBuilder();

        assertThrows(base.withName("Foo$Bar"), IllegalArgumentException.class);
        assertThrows(base.withName("%"), IllegalArgumentException.class);
        assertThrows(base.withName("a:b"), IllegalArgumentException.class);
        assertThrows(base.withName("a@b"), IllegalArgumentException.class);
        assertThrows(base.withName("!"), IllegalArgumentException.class);
        assertThrows(base.withName(null), IllegalArgumentException.class);
        assertThrows(base.withName(""), IllegalArgumentException.class);
    }

    @Test
    public void testInvalidHostNames() {
        ServiceEndpointBuilder base = new ServiceEndpointBuilder();

        assertThrows(base.withAddress(null, 8080), IllegalArgumentException.class);
        assertThrows(base.withAddress("", 8080), IllegalArgumentException.class);
    }

    @Test
    public void testInvalidPorts() {
        ServiceEndpointBuilder base = new ServiceEndpointBuilder();

        assertThrows(base.withAddress("localhost", -1), IllegalArgumentException.class);
        assertThrows(base.withAddress("localhost", -2), IllegalArgumentException.class);
        assertThrows(base.withAddress("localhost", 65536), IllegalArgumentException.class);
    }

    @Test
    public void testEquality() {
        ServiceEndpoint endpoint = new ServiceEndpoint("Foo", "server", 80);
        assertEquals(endpoint, endpoint);
        assertEquals(endpoint, new ServiceEndpoint("Foo", "server", 80));
        assertFalse(endpoint.equals(null));
        assertFalse(endpoint.equals(new ServiceEndpoint("Foo", "server", 81)));
        assertFalse(endpoint.equals(new ServiceEndpoint("Foo", "server2", 80)));
        assertFalse(endpoint.equals(new ServiceEndpoint("Goo", "server", 80)));
        assertFalse(endpoint.equals(new ServiceEndpoint("Foo", "server", 80, "")));
        assertFalse(endpoint.equals(new ServiceEndpoint("Foo", "server", 80, "payload")));
    }

    @Test
    public void testPayloadEqualityHashCode() {
        ServiceEndpoint endpoint = new ServiceEndpoint("Foo", "server", 80, "payload");
        testEquals(endpoint, new ServiceEndpoint("Foo", "server", 80, "payload"));
        assertFalse(endpoint.equals(new ServiceEndpoint("Foo", "server", 80, "")));
        assertFalse(endpoint.equals(new ServiceEndpoint("Foo", "server", 80)));
    }

    @Test
    public void testRegistrationTimeEqualityHashCode() {
        // The registrationTime should be ignored by the equals() and hashCode() methods since it doesn't
        // distinguish two logical endpoints from each other.
        ServiceEndpoint endpoint = new ServiceEndpoint("Foo", "server", 80);
        testEquals(endpoint, new ServiceEndpoint("Foo", "server", 80, new DateTime(1234567890123L), null));
    }

    @Test
    public void testToJson() throws Exception {
        ServiceEndpoint endpoint = new ServiceEndpoint("FooService", "server", 8080);
        assertJson(endpoint.toJson(), endpoint);
    }

    @Test
    public void testToJsonWithPayload() throws Exception {
        ServiceEndpoint endpoint = new ServiceEndpoint("FooService", "server", 8080, "payload");
        assertJson(endpoint.toJson(), endpoint);
    }

    @Test
    public void testToJsonWithEmptyPayload() throws Exception {
        ServiceEndpoint endpoint = new ServiceEndpoint("FooService", "server", 8080, "");
        assertJson(endpoint.toJson(), endpoint);
    }

    @Test
    public void testFromJson() throws Exception {
        ServiceEndpoint endpoint = new ServiceEndpoint("FooService", "server", 8080);
        assertEquals(endpoint, ServiceEndpoint.fromJson(endpoint.toJson()));
    }

    @Test
    public void testFromJsonWithPayload() throws Exception {
        ServiceEndpoint endpoint = new ServiceEndpoint("FooService", "server", 8080, "payload");
        assertEquals(endpoint, ServiceEndpoint.fromJson(endpoint.toJson()));
    }

    @Test
    public void testFromJsonWithEmptyPayload() throws Exception {
        ServiceEndpoint endpoint = new ServiceEndpoint("FooService", "server", 8080, "");
        assertEquals(endpoint, ServiceEndpoint.fromJson(endpoint.toJson()));
    }

    @Test(expected = AssertionError.class)
    public void testFromJsonWithMalformedJson() throws Exception {
        ServiceEndpoint.fromJson("{");
    }

    @Test(expected = NullPointerException.class)
    public void testFromJsonWithMissingName() throws Exception {
        ServiceEndpoint.fromJson(buildJson("name"));
    }

    @Test(expected = NullPointerException.class)
    public void testFromJsonWithMissingHost() throws Exception {
        ServiceEndpoint.fromJson(buildJson("host"));
    }

    @Test(expected = NullPointerException.class)
    public void testFromJsonWithMissingPort() throws Exception {
        ServiceEndpoint.fromJson(buildJson("port"));
    }

    @Test(expected = NullPointerException.class)
    public void testFromJsonWithMissingRegistrationTime() throws Exception {
        ServiceEndpoint.fromJson(buildJson("registration-time"));
    }

    @Test
    public void testFromJsonWithMissingPayload() throws Exception {
        assertEquals(null, ServiceEndpoint.fromJson(buildJson("payload")).getPayload());
    }

    private void assertThrows(ServiceEndpointBuilder builder, Class<? extends Throwable> cls) {
        String expectedClassName = cls.getSimpleName();

        try {
            builder.build();
            fail("No exception thrown, expected " + expectedClassName + " to be thrown.");
        } catch (AssertionError e) {
            // Don't check the type of AssertionError exceptions, those happen when we fail to throw an exception.
            throw e;
        } catch (Throwable t) {
            if (!cls.isInstance(t)) {
                String actualClassName = t.getClass().getSimpleName();
                fail("Expected " + expectedClassName + " to be thrown, instead " + actualClassName + " was thrown.");
            }
        }
    }

    private void testEquals(Object expected, Object actual) {
        assertEquals(expected, actual);
        assertEquals(expected.hashCode(), actual.hashCode());
    }

    private void assertJson(String json, ServiceEndpoint endpoint) throws Exception {
        JsonNode root = new ObjectMapper().readTree(json);

        assertEquals(endpoint.getServiceName(), root.get("name").getTextValue());
        assertEquals(endpoint.getHostname(), root.get("host").getTextValue());
        assertEquals(endpoint.getPort(), root.get("port").getIntValue());
        assertEquals(endpoint.getRegistrationTime(), ISO8601.parseDateTime(root.get("registration-time").getTextValue()));

        if (endpoint.getPayload() != null) {
            assertEquals(endpoint.getPayload(), root.get("payload").getTextValue());
        } else {
            assertNull(root.get("payload").getTextValue());
        }
    }

    private String buildJson(String without) throws IOException {
        StringWriter writer = new StringWriter();

        JsonGenerator generator = new JsonFactory().createJsonGenerator(writer);
        generator.writeStartObject();
        if (!"registration-time".equals(without)) {
            generator.writeStringField("registration-time", ISO8601.print(DateTime.now()));
        }

        if (!"name".equals(without)) {
            generator.writeStringField("name", "serviceName");
        }

        if (!"host".equals(without)) {
            generator.writeStringField("host", "server");
        }

        if (!"port".equals(without)) {
            generator.writeNumberField("port", 8080);
        }

        if (!"payload".equals(without)) {
            generator.writeStringField("payload", "payload");
        }
        generator.writeEndObject();
        generator.close();

        return writer.toString();
    }

    private static final class ServiceEndpointBuilder
    {
        private final String _serviceName;
        private final String _hostname;
        private final int _port;
        private final String _payload;

        public ServiceEndpointBuilder() {
            this("Foo", "localhost", 8080, null);
        }

        public ServiceEndpointBuilder(String serviceName, String hostname, int port, String payload) {
            _serviceName = serviceName;
            _hostname = hostname;
            _port = port;
            _payload = payload;
        }

        public ServiceEndpointBuilder withName(String serviceName) {
            return new ServiceEndpointBuilder(serviceName, _hostname, _port, _payload);
        }

        public ServiceEndpointBuilder withAddress(String hostname, int port) {
            return new ServiceEndpointBuilder(_serviceName, hostname, port, _payload);
        }

        public ServiceEndpoint build() {
            return new ServiceEndpoint(_serviceName, _hostname, _port, _payload);
        }
    }
}
