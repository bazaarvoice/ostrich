package com.bazaarvoice.soa.metrics;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Objects;
import com.google.common.base.Strings;
import com.yammer.metrics.core.Counter;
import com.yammer.metrics.core.Gauge;
import com.yammer.metrics.core.Histogram;
import com.yammer.metrics.core.Meter;
import com.yammer.metrics.core.MetricName;
import com.yammer.metrics.core.MetricsRegistry;
import com.yammer.metrics.core.Timer;

import java.io.Closeable;
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

    /** Set the metrics registry that should be used by the SOA library for creating and registering metrics. */
    public static void setMetricsRegistry(MetricsRegistry registry) {
        DEFAULT_METRICS_REGISTRY = checkNotNull(registry);
    }

    private final MetricsRegistry _registry;
    private final Class<?> _domain;

    public Metrics(Class<?> owner) {
        this(DEFAULT_METRICS_REGISTRY, owner);
    }

    @VisibleForTesting
    Metrics(MetricsRegistry registry, Class<?> owner) {
        checkNotNull(owner);
        checkNotNull(registry);

        _registry = registry;
        _domain = owner;
    }

    public void close() {
        MetricName template = newName("", "");
        for (MetricName name : _registry.allMetrics().keySet()) {
            if (Objects.equal(name.getGroup(), template.getGroup()) &&
                    Objects.equal(name.getType(), template.getType())) {
                _registry.removeMetric(name);
            }
        }
    }

    /** @see MetricsRegistry#newGauge(MetricName, Gauge) */
    public <T> Gauge<T> newGauge(String scope, String name, Gauge<T> metric) {
        checkNotNullOrEmpty(scope);
        checkNotNullOrEmpty(name);
        checkNotNull(metric);
        return _registry.newGauge(newName(scope, name), metric);
    }

    /** @see MetricsRegistry#newCounter(com.yammer.metrics.core.MetricName) */
    public Counter newCounter(String scope, String name) {
        checkNotNullOrEmpty(scope);
        checkNotNullOrEmpty(name);
        return _registry.newCounter(newName(scope, name));
    }

    /** @see MetricsRegistry#newHistogram(MetricName, boolean) */
    public Histogram newHistogram(String scope, String name, boolean biased) {
        checkNotNullOrEmpty(scope);
        checkNotNullOrEmpty(name);
        return _registry.newHistogram(newName(scope, name), biased);
    }

    /** @see MetricsRegistry#newMeter(MetricName, String, TimeUnit) */
    public Meter newMeter(String scope, String name, String eventType, TimeUnit unit) {
        checkNotNullOrEmpty(scope);
        checkNotNullOrEmpty(name);
        checkNotNullOrEmpty(eventType);
        checkNotNull(unit);
        return _registry.newMeter(newName(scope, name), eventType, unit);
    }

    /** @see MetricsRegistry#newTimer(MetricName, TimeUnit, TimeUnit) */
    public Timer newTimer(String scope, String name, TimeUnit durationUnit, TimeUnit rateUnit) {
        checkNotNullOrEmpty(scope);
        checkNotNullOrEmpty(name);
        checkNotNull(durationUnit);
        checkNotNull(rateUnit);
        return _registry.newTimer(newName(scope, name), durationUnit, rateUnit);
    }

    @VisibleForTesting
    MetricName newName(String scope, String name) {
        return new MetricName(_domain, name, scope);
    }

    @VisibleForTesting
    MetricsRegistry getRegistry() {
        return _registry;
    }

    private static void checkNotNullOrEmpty(String name) {
        checkNotNull(name);
        checkArgument(!Strings.isNullOrEmpty(name));
    }
}
