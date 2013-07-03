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

import com.google.common.reflect.Reflection;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Test helper that exposes package-private methods on {@link ServicePoolProxy}.
 */
public class ServicePoolProxyHelper {
    public static <S> S createMock(Class<S> serviceType, com.bazaarvoice.ostrich.ServicePool<S> pool) {
        @SuppressWarnings("unchecked")
        ServicePoolProxy<S> servicePoolProxy = mock(ServicePoolProxy.class);
        when(servicePoolProxy.getServicePool()).thenReturn(pool);
        return Reflection.newProxy(serviceType, servicePoolProxy);
    }
}
