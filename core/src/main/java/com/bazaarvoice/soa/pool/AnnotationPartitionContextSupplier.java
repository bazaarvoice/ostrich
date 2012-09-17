package com.bazaarvoice.soa.pool;

import com.bazaarvoice.soa.PartitionContext;
import com.bazaarvoice.soa.PartitionContextBuilder;
import com.bazaarvoice.soa.partition.PartitionKey;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Map;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

/**
 * Builds {@link PartitionContext} objects for service pool calls based on {@link PartitionKey} annotations.  This is
 * designed for use by the {@link ServicePoolProxy} if/when the proxy invocation handler can determine the partition
 * context from method arguments.
 */
class AnnotationPartitionContextSupplier implements PartitionContextSupplier {
    private final Map<Method, String[]> _keyMappings;

    /**
     * Introspects the specified service interface and client implementation class, looking for {@link PartitionKey}
     * annotations on the implementation class.
     * <p>
     * At runtime the {@code Method} passed to the {@link #forCall(java.lang.reflect.Method, Object[])} method is
     * expected to belong to the interface class.  But the interface shouldn't have {@link PartitionKey} annotations
     * since that's an implementation concern.  As a result, this constructor expects the annotations to be found on the
     * implementation class.
     */
    <S> AnnotationPartitionContextSupplier(Class<S> ifc, Class<? extends S> impl) {
        checkArgument(ifc.isAssignableFrom(impl));

        ImmutableMap.Builder<Method, String[]> builder = ImmutableMap.builder();
        for (Method ifcMethod : ifc.getDeclaredMethods()) {
            Method implMethod;
            try {
                implMethod = impl.getMethod(ifcMethod.getName(), ifcMethod.getParameterTypes());
            } catch (NoSuchMethodException e) {
                throw Throwables.propagate(e);  // Should never happen if impl implements ifc.
            }

            String[] keyMappings = collectPartitionKeyAnnotations(implMethod);
            if (keyMappings == null) {
                continue;  // Not annotated
            }

            // Index by the ifcMethod because that's the method provided when a dynamic proxy method is invoked.
            builder.put(ifcMethod, keyMappings);
        }
        _keyMappings = builder.build();
    }

    @Override
    public PartitionContext forCall(Method method, Object... args) {
        String[] mappings = _keyMappings.get(method);
        if (mappings == null) {
            return PartitionContextBuilder.empty();
        }

        PartitionContextBuilder builder = new PartitionContextBuilder();
        for (int i = 0; i < mappings.length; i++) {
            if (mappings[i] != null && args[i] != null) {
                builder.put(mappings[i], args[i]);
            }
        }
        return builder.build();
    }

    /**
     * Returns an array indexed by argument index with the value of the @PartitionKey annotation for each argument,
     * or null if no arguments are annotated with @PartitionKey.
     */
    private String[] collectPartitionKeyAnnotations(Method method) {
        Annotation[][] annotations = method.getParameterAnnotations();
        String[] keyMappings = new String[annotations.length];
        boolean keyMappingFound = false;
        Map<String, Integer> unique = Maps.newHashMap();
        for (int i = 0; i < annotations.length; i++) {
            PartitionKey annotation = findPartitionKeyAnnotation(annotations[i]);
            if (annotation == null) {
                continue;
            }
            String key = checkNotNull(annotation.value());
            Integer prev = unique.put(key, i);
            checkState(prev == null, "Method '%s' has multiple arguments annotated with the same @PartitionKey " +
                    "value '%s': arguments %s and %s", method, key, prev, i);
            keyMappings[i] = key;
            keyMappingFound = true;
        }
        return keyMappingFound ? keyMappings : null;
    }

    private static PartitionKey findPartitionKeyAnnotation(Annotation[] annotations) {
        for (Annotation annotation : annotations) {
            if (annotation instanceof PartitionKey) {
                return (PartitionKey) annotation;
            }
        }
        return null;
    }
}
