package com.bazaarvoice.soa;

import com.google.common.collect.ImmutableMap;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class PartitionContextBuilderTest {
    private static final String FOO_KEY = "foo";
    private static final String BAR_KEY = "bar";
    private static final String FOO_OBJECT = "foo object";
    private static final Object BAR_OBJECT = "bar object";
    private static final Object DEFAULT_OBJECT = "default object";

    @Test
    public void testEmpty() {
        assertTrue(PartitionContextBuilder.empty().asMap().isEmpty());
        assertNull(PartitionContextBuilder.empty().get());
    }

    @Test
    public void testOfNoKey() {
        PartitionContext context = PartitionContextBuilder.of(DEFAULT_OBJECT);

        assertSame(DEFAULT_OBJECT, context.get());
        assertEquals(ImmutableMap.of("", DEFAULT_OBJECT), context.asMap());
    }

    @Test
    public void testOfOneKey() {
        PartitionContext context = PartitionContextBuilder.of(FOO_KEY, FOO_OBJECT);

        assertSame(FOO_OBJECT, context.get(FOO_KEY));
        assertEquals(ImmutableMap.of(FOO_KEY, FOO_OBJECT), context.asMap());
    }

    @Test
    public void testOfTwoKeys() {
        PartitionContext context = PartitionContextBuilder.of(FOO_KEY, FOO_OBJECT, BAR_KEY, BAR_OBJECT);

        assertSame(FOO_OBJECT, context.get(FOO_KEY));
        assertSame(BAR_OBJECT, context.get(BAR_KEY));
        assertEquals(ImmutableMap.of(FOO_KEY, FOO_OBJECT, BAR_KEY, BAR_OBJECT), context.asMap());
    }

    @Test
    public void testPut() {
        PartitionContext context = new PartitionContextBuilder().put(FOO_KEY, FOO_OBJECT).build();

        assertSame(FOO_OBJECT, context.get(FOO_KEY));
        assertEquals(ImmutableMap.of(FOO_KEY, FOO_OBJECT), context.asMap());
    }

    @Test
    public void testPutAll() {
        Map<String, Object> map = ImmutableMap.of(FOO_KEY, FOO_OBJECT, BAR_KEY, BAR_OBJECT);
        PartitionContext context = new PartitionContextBuilder().putAll(map).build();

        assertSame(FOO_OBJECT, context.get(FOO_KEY));
        assertSame(BAR_OBJECT, context.get(BAR_KEY));
        assertEquals(ImmutableMap.of(FOO_KEY, FOO_OBJECT, BAR_KEY, BAR_OBJECT), context.asMap());
    }
}
