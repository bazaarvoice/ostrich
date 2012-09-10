package com.bazaarvoice.soa.examples.calculator.client;

import com.bazaarvoice.soa.ServiceEndPoint;
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
