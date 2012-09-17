package com.bazaarvoice.soa;

import java.util.Map;

/**
 * A map of key/value pairs that a service pool can use to choose among available back-end service end points.
 */
public interface PartitionContext {
    /**
     * Gets the default piece of context. In many cases, there is only a single piece of relevant context, which this
     * method should provide.
     *
     * @return The default context data.
     */
    Object get();

    /**
     * Gets the context for the specified key.
     * @param key The key for the desired context data.
     * @return The context data.
     */
    Object get(String key);

    /**
     * Gets a {@code Map} version of the context. The Map should be immutable.
     * @return A {@code Map} with the same key/value pairs as this context.
     */
    Map<String, Object> asMap();
}
