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

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.yammer.metrics.core.Gauge;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.Set;

class InstanceGauge extends Gauge<Integer> {
    private final Set<Reference<?>> _instances = Sets.newSetFromMap(Maps.<Reference<?>, Boolean>newConcurrentMap());
    private final ReferenceQueue<Object> _referenceQueue = new ReferenceQueue<Object>();

    @Override
    public Integer value() {
        cleanup();
        return _instances.size();
    }

    Reference<?> add(Object object) {
        Reference<Object> reference = new WeakReference<Object>(object, _referenceQueue);
        _instances.add(reference);
        return reference;
    }

    void remove(Reference<?> reference) {
        _instances.remove(reference);
    }

    private void cleanup() {
        Reference<?> reference = _referenceQueue.poll();
        while (reference != null) {
            _instances.remove(reference);
            reference = _referenceQueue.poll();
        }
    }
}
