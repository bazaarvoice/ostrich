package com.bazaarvoice.soa.pool;

import java.util.concurrent.TimeUnit;

import static com.bazaarvoice.soa.pool.ServiceCachingPolicy.ExhaustionAction;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

public class ServiceCachingPolicyBuilder {
    public static final ServiceCachingPolicy NO_CACHING = new ServiceCachingPolicyBuilder()
            .withMaxNumConnections(0)
            .withMaxNumConnectionsPerEndpoint(0)
            .withCacheExhaustionAction(ExhaustionAction.GROW)
            .build();

    private int _maxNumConnections = -1;
    private int _maxNumConnectionsPerEndPoint = -1;
    private long _maxConnectionIdleTimeMillis;
    private ExhaustionAction _cacheExhaustionAction = ExhaustionAction.GROW;

    public ServiceCachingPolicyBuilder withMaxNumConnections(int maxNumConnections) {
        checkState(maxNumConnections >= -1);

        _maxNumConnections = maxNumConnections;
        return this;
    }

    public ServiceCachingPolicyBuilder withMaxNumConnectionsPerEndpoint(int maxNumConnectionsPerEndPoint) {
        checkState(maxNumConnectionsPerEndPoint >= -1);

        _maxNumConnectionsPerEndPoint = maxNumConnectionsPerEndPoint;
        return this;
    }

    public ServiceCachingPolicyBuilder withMaxConnectionIdleTime(int maxConnectionIdleTime, TimeUnit unit) {
        checkState(maxConnectionIdleTime > 0);
        checkNotNull(unit);

        _maxConnectionIdleTimeMillis = unit.toMillis(maxConnectionIdleTime);
        return this;
    }

    public ServiceCachingPolicyBuilder withCacheExhaustionAction(ExhaustionAction action) {
        checkNotNull(action);

        _cacheExhaustionAction = action;
        return this;
    }

    public ServiceCachingPolicy build() {
        checkState(_maxNumConnections == -1 || _maxNumConnectionsPerEndPoint <= _maxNumConnections);

        final int maxNumConnections = _maxNumConnections;
        final int maxNumConnectionsPerEndPoint = _maxNumConnectionsPerEndPoint;
        final long maxConnectionIdleTimeMillis = _maxConnectionIdleTimeMillis;
        final ExhaustionAction cacheExhaustionAction = _cacheExhaustionAction;

        return new ServiceCachingPolicy() {
            @Override
            public int getMaxNumServiceInstances() {
                return maxNumConnections;
            }

            @Override
            public int getMaxNumServiceInstancesPerEndPoint() {
                return maxNumConnectionsPerEndPoint;
            }

            @Override
            public long getMinIdleTimeBeforeEviction(TimeUnit unit) {
                return unit.convert(maxConnectionIdleTimeMillis, TimeUnit.MILLISECONDS);
            }

            @Override
            public ExhaustionAction getCacheExhaustionAction() {
                return cacheExhaustionAction;
            }
        };
    }
}
