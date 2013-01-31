package com.bazaarvoice.ostrich.pool;

import com.bazaarvoice.ostrich.PartitionContext;
import com.bazaarvoice.ostrich.PartitionContextBuilder;

import java.lang.reflect.Method;

/**
 * A supplier with a {@link #forCall} method that always returns {@link PartitionContextBuilder#empty()}.
 */
public class EmptyPartitionContextSupplier implements PartitionContextSupplier {
    @Override
    public PartitionContext forCall(Method method, Object... args) {
        return PartitionContextBuilder.empty();
    }
}
