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
package com.bazaarvoice.ostrich.examples.dictionary.client;

import com.bazaarvoice.ostrich.ServiceEndPoint;
import com.bazaarvoice.ostrich.partition.PartitionKey;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.UniformInterfaceException;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import java.net.URI;

import static com.google.common.base.Preconditions.checkNotNull;

public class DictionaryClient implements DictionaryService {
    private final Client _client;
    private final UriBuilder _service;

    public DictionaryClient(ServiceEndPoint endPoint, Client jerseyClient) {
        this(Payload.valueOf(endPoint.getPayload()).getServiceUrl(), jerseyClient);
    }

    public DictionaryClient(URI endPoint, Client jerseyClient) {
        _client = checkNotNull(jerseyClient, "jerseyClient");
        _service = UriBuilder.fromUri(endPoint);
    }

    @Override
    public boolean contains(@PartitionKey String word) {
        try {
            URI uri = _service.clone().segment("contains", word).build();
            return _client.resource(uri).get(Boolean.class);
        } catch (UniformInterfaceException e) {
            throw convertException(e);
        }
    }

    private RuntimeException convertException(UniformInterfaceException e) {
        ClientResponse response = e.getResponse();
        String exceptionType = response.getHeaders().getFirst("X-BV-Exception");

        if (response.getStatus() == Response.Status.BAD_REQUEST.getStatusCode() &&
                IllegalArgumentException.class.getName().equals(exceptionType)) {
            return new IllegalArgumentException(response.getEntity(String.class), e);
        }
        return e;
    }
}
