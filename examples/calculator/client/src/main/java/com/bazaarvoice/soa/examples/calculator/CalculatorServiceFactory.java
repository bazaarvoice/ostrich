package com.bazaarvoice.soa.examples.calculator;

import com.bazaarvoice.soa.LoadBalanceAlgorithm;
import com.bazaarvoice.soa.ServiceEndpoint;
import com.bazaarvoice.soa.ServiceFactory;
import com.bazaarvoice.soa.loadbalance.RandomAlgorithm;

import java.util.Map;

import static com.google.common.base.Preconditions.checkNotNull;

public class CalculatorServiceFactory implements ServiceFactory<CalculatorService> {
    @Override
    public String getServiceName() {
        return "calculator";
    }

    @Override
    public LoadBalanceAlgorithm getLoadBalanceAlgorithm() {
        return new RandomAlgorithm();
    }

    @Override
    public CalculatorService create(ServiceEndpoint endpoint) {
        return new CalculatorClient(endpoint);
    }

    @Override
    public boolean isHealthy(ServiceEndpoint endpoint) {
        Map<?,?> payload = JsonHelper.fromJson(endpoint.getPayload(), Map.class);
        Http http = new Http((String) checkNotNull(payload.get("adminUrl")));

        return http.HEAD("/healthcheck") == 200;
    }
}
