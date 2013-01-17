package com.bazaarvoice.soa;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class JsonHelperTest {
    @Test
    public void testToJson() {
        Foo foo = foo(1, "Hello World");
        assertEquals("{\"bar\":1,\"baz\":\"Hello World\"}", JsonHelper.toJson(foo));
    }

    @Test
    public void testFromJson() {
        Foo foo = JsonHelper.fromJson("{'bar':1, 'baz':'Hello World'}", Foo.class);
        assertEquals(1, foo.bar);
        assertEquals("Hello World", foo.baz);
    }

    @Test(expected = AssertionError.class)
    public void testFromJsonWithExtraProperties() {
        Foo foo = JsonHelper.fromJson("{'foo':1, 'bar':2, 'baz':'Hello World'}", Foo.class);
        assertEquals(1, foo.bar);
        assertEquals("Hello World", foo.baz);
    }

    @Test(expected = AssertionError.class)
    public void testFromJsonWithMalformedJson() {
        JsonHelper.fromJson("{", int.class);
    }

    @Test
    public void testFromJsonAbleToReadToJson() {
        Foo foo = foo(1, "Hello World");
        Foo bar = JsonHelper.fromJson(JsonHelper.toJson(foo), Foo.class);
        assertEquals(foo.bar, bar.bar);
        assertEquals(foo.baz, bar.baz);
    }

    private static Foo foo(int bar, String baz) {
        Foo foo = new Foo();
        foo.bar = bar;
        foo.baz = baz;
        return foo;
    }

    private static class Foo {
        @JsonProperty private int bar = 0;
        @JsonProperty private String baz = null;
    }
}
