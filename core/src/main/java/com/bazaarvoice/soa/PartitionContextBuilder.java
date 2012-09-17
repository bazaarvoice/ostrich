package com.bazaarvoice.soa;

import com.google.common.collect.ImmutableMap;

import java.util.Map;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * A builder for creating immutable {@link com.bazaarvoice.soa.PartitionContextBuilder} instances.
 * <p>
 * For small partition contexts, the {@code PartitionContext.of()} methods are more convenient.
 */
public final class PartitionContextBuilder {
    private static final PartitionContext EMPTY = new Context(ImmutableMap.<String, Object>of());

    private final ImmutableMap.Builder<String, Object> _map = ImmutableMap.builder();

    public static PartitionContext empty() {
        return EMPTY;
    }

    public static PartitionContext of(Object obj) {
        return new Context(ImmutableMap.of("", obj));
    }

    public static PartitionContext of(String key, Object value) {
        return new Context(ImmutableMap.of(key, value));
    }

    public static PartitionContext of(String key1, Object value1, String key2, Object value2) {
        return new Context(ImmutableMap.of(key1, value1, key2, value2));
    }

    /**
     * Adds the specified key and value to the partition context.  Null keys or values and duplicate keys are not
     * allowed.
     *
     * @return this
     */
    public PartitionContextBuilder put(String key, Object value) {
        _map.put(key, value);
        return this;
    }

    /**
     * Adds the specified keys and values to the partition context.  Null keys or values and duplicate keys are not
     * allowed.
     *
     * @return this
     */
    public PartitionContextBuilder putAll(Map<String, ?> map) {
        _map.putAll(map);
        return this;
    }

    /**
     * Returns a newly-create immutable {@code PartitionContext}.
     */
    public PartitionContext build() {
        return new Context(_map.build());
    }

    private static class Context implements PartitionContext {
        private final ImmutableMap<String, Object> _map;

        private Context(ImmutableMap<String, Object> map) {
            _map = checkNotNull(map);
        }

        @Override
        public Object get() {
            return _map.get("");
        }

        @Override
        public Object get(String key) {
            return _map.get(key);
        }

        @Override
        public Map<String, Object> asMap() {
            return _map;
        }

        @Override
        public boolean equals(Object o) {
            return this == o || (o instanceof Context && _map.equals(((Context) o)._map));
        }

        @Override
        public int hashCode() {
            return 95261 + _map.hashCode();
        }
    }
}
