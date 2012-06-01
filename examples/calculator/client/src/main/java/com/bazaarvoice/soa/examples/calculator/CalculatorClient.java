package com.bazaarvoice.soa.examples.calculator;

import com.bazaarvoice.soa.ServiceEndpoint;

import java.util.Map;

import static com.google.common.base.Preconditions.checkNotNull;

public class CalculatorClient implements CalculatorService {
    private final Http _service;

    public CalculatorClient(ServiceEndpoint endpoint) {
        Map<?,?> payload = JsonHelper.fromJson(endpoint.getPayload(), Map.class);
        _service = new Http((String) checkNotNull(payload.get("url")));
    }

    @Override
    public int add(int a, int b) {
        return Integer.parseInt(_service.GET("/add/" + a + "/" + b));
    }

    @Override
    public int sub(int a, int b) {
        return Integer.parseInt(_service.GET("/sub/" + a + "/" + b));
    }

    @Override
    public int mul(int a, int b) {
        return Integer.parseInt(_service.GET("/mul/" + a + "/" + b));
    }

    @Override
    public int div(int a, int b) {
        return Integer.parseInt(_service.GET("/div/" + a + "/" + b));
    }
}
