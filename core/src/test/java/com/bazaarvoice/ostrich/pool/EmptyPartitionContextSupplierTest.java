/*
 * Copyright 2013 Bazaarvoice, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.bazaarvoice.ostrich.pool;

import com.bazaarvoice.ostrich.PartitionContext;
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
