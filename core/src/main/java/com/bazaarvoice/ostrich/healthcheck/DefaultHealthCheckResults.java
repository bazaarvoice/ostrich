package com.bazaarvoice.ostrich.healthcheck;

import com.bazaarvoice.ostrich.HealthCheckResult;
import com.bazaarvoice.ostrich.HealthCheckResults;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

import java.util.List;

import static com.google.common.base.Preconditions.checkNotNull;

public final class DefaultHealthCheckResults implements HealthCheckResults {
    private final List<HealthCheckResult> _results = Lists.newArrayList();

    @Override
    public boolean hasHealthyResult() {
        return getHealthyResult() != null;
    }

    @Override
    public Iterable<HealthCheckResult> getAllResults() {
        return Iterables.unmodifiableIterable(_results);
    }

    @Override
    public HealthCheckResult getHealthyResult() {
        return Iterables.tryFind(_results, new Predicate<HealthCheckResult>() {
            @Override
            public boolean apply(HealthCheckResult result) {
                return result.isHealthy();
            }
        }).orNull();
    }

    @Override
    public Iterable<HealthCheckResult> getUnhealthyResults() {
        return Iterables.filter(_results, new Predicate<HealthCheckResult>() {
            @Override
            public boolean apply(HealthCheckResult result) {
                return !result.isHealthy();
            }
        });
    }

    public void addHealthCheckResult(HealthCheckResult result) {
        checkNotNull(result);
        _results.add(result);
    }
}