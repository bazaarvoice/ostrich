package com.bazaarvoice.soa.pool;

import com.bazaarvoice.soa.PartitionContext;
import com.bazaarvoice.soa.PartitionContextBuilder;

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
