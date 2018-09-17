package com.bazaarvoice.ostrich.examples.dictionary.client;

import com.bazaarvoice.ostrich.ServiceEndPoint;
import com.bazaarvoice.ostrich.partition.PartitionKey;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.client.Client;
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
            return _client
                    .target(_service)
                    .path("contains")
                    .path(word)
                    .request()
                    .get(Boolean.class);
        } catch (WebApplicationException e) {
            throw convertException(e);
        }
    }

    private RuntimeException convertException(WebApplicationException e) {
        Response response = e.getResponse();
        String exceptionType = response.getStringHeaders().getFirst("X-BV-Exception");

        if (response.getStatus() == Response.Status.BAD_REQUEST.getStatusCode() &&
                IllegalArgumentException.class.getName().equals(exceptionType)) {
            return new IllegalArgumentException(response.readEntity(String.class), e);
        }
        return e;
    }
}
