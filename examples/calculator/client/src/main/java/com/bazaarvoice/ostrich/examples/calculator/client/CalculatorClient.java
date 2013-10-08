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
package com.bazaarvoice.ostrich.examples.calculator.client;

import com.bazaarvoice.ostrich.ServiceEndPoint;
import com.sun.jersey.api.client.Client;

import javax.ws.rs.core.UriBuilder;
import java.net.URI;

import static com.google.common.base.Preconditions.checkNotNull;

public class CalculatorClient implements CalculatorService {
    private final Client _client;
    private final UriBuilder _service;

    public CalculatorClient(ServiceEndPoint endPoint, Client jerseyClient) {
        this(Payload.valueOf(endPoint.getPayload()).getServiceUrl(), jerseyClient);
    }

    public CalculatorClient(URI endPoint, Client jerseyClient) {
        _client = checkNotNull(jerseyClient, "jerseyClient");
        _service = UriBuilder.fromUri(endPoint);
    }

    @Override
    public int add(int a, int b) {
        return call("add", a, b);
    }

    @Override
    public int sub(int a, int b) {
        return call("sub", a, b);
    }

    @Override
    public int mul(int a, int b) {
        return call("mul", a, b);
    }

    @Override
    public int div(int a, int b) {
        return call("div", a, b);
    }

    private int call(String op, int a, int b) {
        URI uri = _service.clone().segment(op, Integer.toString(a), Integer.toString(b)).build();
        return _client.resource(uri).get(Integer.class);
    }
}
