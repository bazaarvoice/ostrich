package com.bazaarvoice.soa.examples.calculator;

import com.bazaarvoice.soa.LoadBalanceAlgorithm;
import com.bazaarvoice.soa.ServiceEndPoint;
import com.bazaarvoice.soa.ServiceFactory;
import com.bazaarvoice.soa.ServicePoolStatistics;
import com.bazaarvoice.soa.exceptions.ServiceException;
import com.bazaarvoice.soa.loadbalance.RandomAlgorithm;

import java.util.Map;

import static com.google.common.base.Preconditions.checkNotNull;

public class CalculatorServiceFactory implements ServiceFactory<CalculatorService> {
    @Override
    public String getServiceName() {
        return "calculator";
    }

    @Override
    public LoadBalanceAlgorithm getLoadBalanceAlgorithm(ServicePoolStatistics stats) {
        return new RandomAlgorithm();
    }

    @Override
    public CalculatorService create(ServiceEndPoint endPoint) {
        return new CalculatorClient(endPoint);
    }

    @Override
    public boolean isRetriableException(Exception exception) {
        return exception instanceof ServiceException;
    }

    @Override
    public boolean isHealthy(ServiceEndPoint endPoint) {
        Map<?,?> payload = JsonHelper.fromJson(endPoint.getPayload(), Map.class);
        Http http = new Http((String) checkNotNull(payload.get("adminUrl")));

        return http.HEAD("/healthcheck") == 200;
    }
}
