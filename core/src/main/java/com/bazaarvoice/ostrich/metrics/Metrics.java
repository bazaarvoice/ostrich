package com.bazaarvoice.ostrich.metrics;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Objects;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.yammer.metrics.core.Counter;
import com.yammer.metrics.core.Gauge;
import com.yammer.metrics.core.Histogram;
import com.yammer.metrics.core.Meter;
import com.yammer.metrics.core.MetricName;
import com.yammer.metrics.core.MetricsRegistry;
import com.yammer.metrics.core.Timer;

import java.io.Closeable;
import java.lang.ref.Reference;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * A thin wrapper implementation around Yammer Metrics for use by SOA.  This wrapper adds the following functionality:
 * <p/>
 * The ability to control what {@link MetricsRegistry} instance is used for SOA metrics via
 * {@link #setMetricsRegistry(MetricsRegistry)}.  This gives us the ability in the future to isolate the SOA metrics
 * from the end user's application metrics as well as publish them somewhere differently from the end user's application
 * metrics.
 */
public class Metrics implements Closeable {
    private static MetricsRegistry DEFAULT_METRICS_REGISTRY = com.yammer.metrics.Metrics.defaultRegistry();

    private InstanceGauge _instanceGauge = new InstanceGauge();
    private String _instanceScope = null;
    private final List<Reference<?>> _instanceReferences = Lists.newLinkedList();
    private final List<MetricName> _registeredMetrics = Lists.newLinkedList();

    /** Set the metrics registry that should be used by the SOA library for creating and registering metrics. */
    public static void setMetricsRegistry(MetricsRegistry registry) {
        DEFAULT_METRICS_REGISTRY = checkNotNull(registry);
    }

    private final MetricsRegistry _registry;
    private final Class<?> _domain;

    public static Metrics forClass(Class<?> owner) {
        return new Metrics(DEFAULT_METRICS_REGISTRY, owner);
    }

    public static Metrics forInstance(Object instance, String instanceScope) {
        checkNotNull(instance);
        Metrics metrics = forClass(instance.getClass());
        metrics.addInstance(instance, instanceScope);
        return metrics;
    }

    private Metrics(MetricsRegistry registry, Class<?> owner) {
        checkNotNull(owner);
        checkNotNull(registry);

        _registry = registry;
        _domain = owner;
    }

    public void close() {
        for (Reference<?> reference : _instanceReferences) {
            _instanceGauge.remove(reference);
        }
        for (MetricName metricName : _registeredMetrics) {
            // Don't unregister if we have other instances with the same scope.
            if (_instanceGauge.value() == 0 || !Objects.equal(_instanceScope, metricName.getScope())) {
                _registry.removeMetric(metricName);
            }
        }
    }

    /** @see MetricsRegistry#newGauge(MetricName, Gauge) */
    public <T> Gauge<T> newGauge(String scope, String name, Gauge<T> metric) {
        checkNotNull(metric);
        return _registry.newGauge(newRegisteredName(scope, name), metric);
    }

    /** @see MetricsRegistry#newCounter(com.yammer.metrics.core.MetricName) */
    public Counter newCounter(String scope, String name) {
        return _registry.newCounter(newRegisteredName(scope, name));
    }

    /** @see MetricsRegistry#newHistogram(MetricName, boolean) */
    public Histogram newHistogram(String scope, String name, boolean biased) {
        return _registry.newHistogram(newRegisteredName(scope, name), biased);
    }

    /** @see MetricsRegistry#newMeter(MetricName, String, TimeUnit) */
    public Meter newMeter(String scope, String name, String eventType, TimeUnit unit) {
        checkNotNullOrEmpty(eventType);
        checkNotNull(unit);
        return _registry.newMeter(newRegisteredName(scope, name), eventType, unit);
    }

    /** @see MetricsRegistry#newTimer(MetricName, TimeUnit, TimeUnit) */
    public Timer newTimer(String scope, String name, TimeUnit durationUnit, TimeUnit rateUnit) {
        checkNotNull(durationUnit);
        checkNotNull(rateUnit);
        return _registry.newTimer(newRegisteredName(scope, name), durationUnit, rateUnit);
    }

    @VisibleForTesting
    InstanceGauge addInstance(Object instance, String scope) {
        // Use an existing instance gauge if there is one registered already.
        _instanceGauge = (InstanceGauge) _registry.newGauge(newRegisteredName(scope, "num-instances"), _instanceGauge);
        _instanceReferences.add(_instanceGauge.add(instance));
        _instanceScope = scope;
        return _instanceGauge;
    }

    @VisibleForTesting
    MetricName newName(String scope, String name) {
        return new MetricName(_domain, name, scope);
    }

    private MetricName newRegisteredName(String scope, String name) {
        checkNotNullOrEmpty(scope);
        checkNotNullOrEmpty(name);

        MetricName metricName = newName(scope, name);
        _registeredMetrics.add(metricName);
        return metricName;
    }

    @VisibleForTesting
    MetricsRegistry getRegistry() {
        return _registry;
    }

    private static void checkNotNullOrEmpty(String string) {
        checkNotNull(string);
        checkArgument(!Strings.isNullOrEmpty(string));
    }
}
