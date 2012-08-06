package com.bazaarvoice.soa.examples.calculator;

import com.yammer.metrics.core.HealthCheck;

import javax.ws.rs.core.Response;

public class CalculatorHealthCheck extends HealthCheck {
    protected CalculatorHealthCheck() {
        super("calculator");
    }

    @Override
    protected Result check() throws Exception {
        if (CalculatorService.STATUS_OVERRIDE == Response.Status.OK) {
            return Result.healthy();
        } else {
            return Result.unhealthy("unhealthy by toggle");
        }
    }
}
