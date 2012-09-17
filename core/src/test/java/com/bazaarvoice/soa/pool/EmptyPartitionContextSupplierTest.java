package com.bazaarvoice.soa.pool;

import com.bazaarvoice.soa.PartitionContext;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class EmptyPartitionContextSupplierTest {
    @Test
    public void testEmptyPartitionContext() {
        EmptyPartitionContextSupplier supplier = new EmptyPartitionContextSupplier();

        // Can't mock out Method, so let's just take one from this class.
        PartitionContext context = supplier.forCall(getClass().getMethods()[0]);

        assertTrue(context.asMap().isEmpty());
    }
}
