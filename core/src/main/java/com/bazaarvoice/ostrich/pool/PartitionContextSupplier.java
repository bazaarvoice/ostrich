package com.bazaarvoice.ostrich.pool;

import com.bazaarvoice.ostrich.PartitionContext;

import java.lang.reflect.Method;

interface PartitionContextSupplier {
    /**
     * Builds a {@link PartitionContext} from the method arguments passed to the specified interface method.
     * <p>
     * If the {@code method} argument's declaring class is not the public service interface this will return an empty
     * partition context.
     */
    PartitionContext forCall(Method method, Object... args);
}
