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
