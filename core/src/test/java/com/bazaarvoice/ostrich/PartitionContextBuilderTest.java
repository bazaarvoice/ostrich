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
