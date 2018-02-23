package com.bazaarvoice.ostrich.metrics;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.common.collect.Sets;

import java.io.Closeable;
import java.util.Set;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * A thin wrapper implementation around Yammer Metrics for use by SOA.  This wrapper adds the following functionality:
 *
 * The ability to control what {@link MetricRegistry} instance is used for SOA metrics via the constructor.  This gives
 * us the ability in the future to isolate the SOA metrics from the end user's application metrics as well as publish
 * them somewhere differently from the end user's application metrics.
 */
public abstract class Metrics implements Closeable {
    /**
     * Create a metrics instance that corresponds to a class.  This is useful for cases where a single instance handles
     * many different services at the same time.  For example a ServiceRegistry.
     *
     * @param metrics the metrics
     * @param cls     the class
     * @return the class metrics
     */
    public static ClassMetrics forClass(MetricRegistry metrics, Class<?> cls) {
        return new ClassMetrics(metrics, cls);
    }

    /**
     * Create a metrics instance that corresponds to a single instance of a class.  This is useful for cases where there
     * exists one instance per service.  For example in a ServicePool.
     *
     * @param metrics     the metrics
     * @param instance    the instance
     * @param serviceName the service name
     * @return the instance metrics
     */
    public static InstanceMetrics forInstance(MetricRegistry metrics, Object instance, String serviceName) {
        return new InstanceMetrics(metrics, instance, serviceName);
    }

    /**
     * The type Class metrics.
     */
    public static final class ClassMetrics implements Closeable {
        private final MetricRegistry _metrics;
        private final Class<?> _class;
        private final String _prefix;
        private final Set<String> _names = Sets.newHashSet();

        /**
         * Instantiates a new Class metrics.
         *
         * @param metrics the metrics
         * @param cls     the cls
         */
        ClassMetrics(MetricRegistry metrics, Class<?> cls) {
            _metrics = checkNotNull(metrics);
            _class = checkNotNull(cls);
            _prefix = MetricRegistry.name(_class);
        }

        /**
         * Gauge gauge.
         *
         * @param <T>         the type parameter
         * @param serviceName the service name
         * @param name        the name
         * @param metric      the metric
         * @return the gauge
         */
        public synchronized <T> Gauge<T> gauge(String serviceName, String name, Gauge<T> metric) {
            checkNotNullOrEmpty(serviceName);
            checkNotNullOrEmpty(name);
            checkNotNull(metric);
            return _metrics.register(name(serviceName, name), metric);
        }

        /**
         * Counter counter.
         *
         * @param serviceName the service name
         * @param name        the name
         * @return the counter
         */
        public synchronized Counter counter(String serviceName, String name) {
            checkNotNullOrEmpty(serviceName);
            checkNotNullOrEmpty(name);
            return _metrics.counter(name(serviceName, name));
        }

        /**
         * Histogram histogram.
         *
         * @param serviceName the service name
         * @param name        the name
         * @return the histogram
         */
        public synchronized Histogram histogram(String serviceName, String name) {
            checkNotNullOrEmpty(serviceName);
            checkNotNullOrEmpty(name);
            return _metrics.histogram(name(serviceName, name));
        }

        /**
         * Meter meter.
         *
         * @param serviceName the service name
         * @param name        the name
         * @return the meter
         */
        public synchronized Meter meter(String serviceName, String name) {
            checkNotNullOrEmpty(serviceName);
            checkNotNullOrEmpty(name);
            return _metrics.meter(name(serviceName, name));
        }

        /**
         * Timer timer.
         *
         * @param serviceName the service name
         * @param name        the name
         * @return the timer
         */
        public synchronized Timer timer(String serviceName, String name) {
            checkNotNullOrEmpty(serviceName);
            checkNotNullOrEmpty(name);
            return _metrics.timer(name(serviceName, name));
        }

        @Override
        public synchronized void close() {
            for (String name : _names) {
                _metrics.remove(name);
            }
            _names.clear();
        }

        /**
         * Name string.
         *
         * @param serviceName the service name
         * @param name        the name
         * @return the string
         */
        @VisibleForTesting
        String name(String serviceName, String name) {
            String fullName = MetricRegistry.name(_prefix, serviceName, name);
            _names.add(fullName);
            return fullName;
        }
    }

    /**
     * The type Instance metrics.
     */
    public static class InstanceMetrics implements Closeable {
        private final MetricRegistry _metrics;
        private final Object _instance;
        private final String _serviceName;
        private final String _prefix;

        private final String _instanceCounterName;
        private final Counter _instanceCounter;
        private final Set<String> _names = Sets.newHashSet();

        /**
         * Instantiates a new Instance metrics.
         *
         * @param metrics     the metrics
         * @param instance    the instance
         * @param serviceName the service name
         */
        InstanceMetrics(MetricRegistry metrics, Object instance, String serviceName) {
            _metrics = checkNotNull(metrics);
            _instance = checkNotNull(instance);
            _serviceName = checkNotNullOrEmpty(serviceName);
            _prefix = MetricRegistry.name(_instance.getClass());
            _instanceCounterName = name("num-instances");
            _instanceCounter = counter("num-instances");
            _instanceCounter.inc();
        }

        /**
         * Gauge gauge.
         *
         * @param <T>   the type parameter
         * @param name  the name
         * @param gauge the gauge
         * @return the gauge
         */
        @SuppressWarnings("unchecked")
        public synchronized <T> Gauge<T> gauge(String name, Gauge<T> gauge) {
            checkNotNullOrEmpty(name);
            checkNotNull(gauge);

            // Unfortunately register doesn't call getOrAdd (probably a bug in metrics), and instead goes through
            // a code path that throws an exception if this metric already exists.
            String fullName = name(name);
            Gauge<T> metric = (Gauge<T>) _metrics.getMetrics().get(fullName);
            if (metric == null) {
                metric = _metrics.register(fullName, gauge);
            }

            return metric;
        }

        /**
         * Counter counter.
         *
         * @param name the name
         * @return the counter
         */
        public synchronized Counter counter(String name) {
            checkNotNullOrEmpty(name);
            return _metrics.counter(name(name));
        }

        /**
         * Histogram histogram.
         *
         * @param name the name
         * @return the histogram
         */
        public synchronized Histogram histogram(String name) {
            checkNotNullOrEmpty(name);
            return _metrics.histogram(name(name));
        }

        /**
         * Meter meter.
         *
         * @param name the name
         * @return the meter
         */
        public synchronized Meter meter(String name) {
            checkNotNullOrEmpty(name);
            return _metrics.meter(name(name));
        }

        /**
         * Timer timer.
         *
         * @param name the name
         * @return the timer
         */
        public synchronized Timer timer(String name) {
            checkNotNullOrEmpty(name);
            return _metrics.timer(name(name));
        }

        @Override
        public synchronized void close() {
            _instanceCounter.dec();
            if (_instanceCounter.getCount() == 0) {
                _metrics.remove(_instanceCounterName);
            }

            for (String name : _names) {
                _metrics.remove(name);
            }
            _names.clear();
        }

        /**
         * Gets instance counter.
         *
         * @return the instance counter
         */
        @VisibleForTesting
        Counter getInstanceCounter() {
            return _instanceCounter;
        }

        /**
         * Name string.
         *
         * @param name the name
         * @return the string
         */
        @VisibleForTesting
        String name(String name) {
            String fullName = MetricRegistry.name(_prefix, _serviceName, name);
            _names.add(fullName);
            return fullName;
        }
    }

    private static String checkNotNullOrEmpty(String string) {
        checkNotNull(string);
        checkArgument(!Strings.isNullOrEmpty(string));
        return string;
    }
}