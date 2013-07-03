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
package com.bazaarvoice.ostrich.metrics;

import org.junit.Test;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;

import static junit.framework.Assert.assertEquals;

public class InstanceGaugeTest {
    private final InstanceGauge _gauge = new InstanceGauge();

    @Test
    public void testValueStartsAtZero() {
        assertEquals(0, _gauge.value().intValue());
    }

    @Test
    public void testValueIncrements() {
        _gauge.add("one");
        assertEquals(1, _gauge.value().intValue());
        _gauge.add("two");
        assertEquals(2, _gauge.value().intValue());
    }

    @Test
    public void testValueDecrements() {
        Reference<?> one = _gauge.add("one");
        _gauge.add("two");

        // Add the reference to its reference queue. Normally, this is done by the garbage collector.
        one.enqueue();
        assertEquals(1, _gauge.value().intValue());
    }

    @Test
    public void testValueMultipleDecrements() {
        Reference<?> one = _gauge.add("one");
        Reference<?> two = _gauge.add("two");

        // Add the reference to its reference queue. Normally, this is done by the garbage collector.
        one.enqueue();
        two.enqueue();
        assertEquals(0, _gauge.value().intValue());
    }
    
    @Test
    public void testRemove() {
        Reference<?> reference = _gauge.add("one");
        _gauge.add("two");

        _gauge.remove(reference);
        assertEquals(1, _gauge.value().intValue());
    }

    @Test
    public void testRemoveMultiple() {
        Reference<?> one = _gauge.add("one");
        Reference<?> two = _gauge.add("two");

        _gauge.remove(one);
        _gauge.remove(two);
        assertEquals(0, _gauge.value().intValue());
    }

    @Test
    public void testRemoveNotAdded() {
        _gauge.add("one");

        _gauge.remove(new WeakReference<Object>("two"));
        assertEquals(1, _gauge.value().intValue());
    }

    @Test
    public void testRemoveRepeatedly() {
        Reference<?> one = _gauge.add("one");
        _gauge.add("two");

        _gauge.remove(one);
        _gauge.remove(one);
        assertEquals(1, _gauge.value().intValue());
    }
}
