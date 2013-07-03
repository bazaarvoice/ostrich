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
package com.bazaarvoice.ostrich.dropwizard.pool;

import com.bazaarvoice.ostrich.ServicePool;
import com.bazaarvoice.ostrich.pool.ServicePoolProxyHelper;
import com.yammer.dropwizard.lifecycle.Managed;
import org.junit.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

public class ManagedServicePoolProxyTest {
    @Test(expected = IllegalArgumentException.class)
    public void testNull() {
        new ManagedServicePoolProxy(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNotProxy() {
        new ManagedServicePoolProxy(mock(Service.class));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testStart() throws Exception {
        ServicePool<Service> pool = mock(ServicePool.class);
        Service service = ServicePoolProxyHelper.createMock(Service.class, pool);
        Managed managed = new ManagedServicePoolProxy(service);

        managed.start();
        verifyZeroInteractions(pool);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testStop() throws Exception {
        ServicePool<Service> pool = mock(ServicePool.class);
        Service service = ServicePoolProxyHelper.createMock(Service.class, pool);
        Managed managed = new ManagedServicePoolProxy(service);

        managed.stop();
        verify(pool).close();
        verifyNoMoreInteractions(pool);
    }

    // A dummy interface for testing...
    private static interface Service {}
}
