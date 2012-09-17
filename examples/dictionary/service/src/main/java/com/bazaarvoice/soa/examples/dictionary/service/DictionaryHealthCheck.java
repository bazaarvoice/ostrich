package com.bazaarvoice.soa.examples.dictionary.service;

import com.yammer.metrics.core.HealthCheck;

import javax.ws.rs.core.Response;

public class DictionaryHealthCheck extends HealthCheck {
    protected DictionaryHealthCheck() {
        super("dictionary");
    }

    @Override
    protected Result check() throws Exception {
        if (DictionaryService.STATUS_OVERRIDE == Response.Status.OK) {
            return Result.healthy();
        } else {
            return Result.unhealthy("unhealthy by toggle");
        }
    }
}
