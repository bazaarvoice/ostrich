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

import com.bazaarvoice.ostrich.RetryPolicy;
import com.bazaarvoice.ostrich.pool.ServicePoolBuilder;
import com.bazaarvoice.ostrich.pool.ServicePoolProxies;
import com.yammer.dropwizard.lifecycle.Managed;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Adapts the Dropwizard {@link Managed} interface for a dynamic service proxy created by
 * {@link ServicePoolBuilder#buildProxy(RetryPolicy)}.  This allows Dropwizard to shutdown service pools cleanly.
 * <p>
 * Here's how to use this class with an instance of a Dropwizard {@code Environment}:
 * <pre>
 *  Environment environment = ...;
 *  Service service = ServicePoolBuilder.create(Service.class)
 *     .withServiceFactory(...)
 *     .buildProxy(...);
 *  environment.manage(new ManagedServicePoolProxy(service));
 * </pre>
 */
public class ManagedServicePoolProxy implements Managed {
    private final Object _proxy;

    /**
     * Wraps the specified dynamic proxy with the Dropwizard {@link Managed} interface.
     * @param proxy A dynamic service proxy created by {@link ServicePoolBuilder#buildProxy(RetryPolicy)}.
     */
    public ManagedServicePoolProxy(Object proxy) {
        checkArgument(ServicePoolProxies.isProxy(proxy));
        _proxy = proxy;
    }

    @Override
    public void start() {
        // Nothing to do
    }

    @Override
    public void stop() {
        ServicePoolProxies.close(_proxy);
    }
}
