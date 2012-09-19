package com.bazaarvoice.soa.metrics;

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
