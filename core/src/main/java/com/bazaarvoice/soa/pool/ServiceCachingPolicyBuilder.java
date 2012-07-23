package com.bazaarvoice.soa.pool;

import java.util.concurrent.TimeUnit;

import static com.bazaarvoice.soa.pool.ServiceCachingPolicy.ExhaustionAction;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

public class ServiceCachingPolicyBuilder {
    public static final ServiceCachingPolicy NO_CACHING = new ServiceCachingPolicyBuilder()
            .withMaxNumServiceInstances(0)
            .withMaxNumServiceInstancesPerEndPoint(0)
            .withCacheExhaustionAction(ExhaustionAction.GROW)
            .build();

    private int _maxNumServiceInstances = -1;
    private int _maxNumServiceInstancesPerEndPoint = -1;
    private long _maxServiceInstanceIdleTimeNanos;
    private ExhaustionAction _cacheExhaustionAction = ExhaustionAction.GROW;

    /**
     * Set the maximum number of cached service instances for the built policy.  If never called, the policy will allow
     * unbounded growth by default.
     *
     * @param maxNumServiceInstances The total maximum number of service instances in the cache.
     * @return this
     */
    public ServiceCachingPolicyBuilder withMaxNumServiceInstances(int maxNumServiceInstances) {
        checkState(maxNumServiceInstances >= 0);

        _maxNumServiceInstances = maxNumServiceInstances;
        return this;
    }

    /**
     * Set the maximum number of cached services instances for a single end point in the built policy. If never called,
     * the policy will allow growth bounded only by the {@link #withMaxNumServiceInstances maxNumServiceInstances}.
     * <p/>
     * NOTE: The per end point maximum must be less than or equal to the total maximum, unless either is unbounded.
     *
     * @param maxNumServiceInstancesPerEndPoint The maximum number of service instances for one end point in the cache.
     * @return this
     */
    public ServiceCachingPolicyBuilder withMaxNumServiceInstancesPerEndPoint(int maxNumServiceInstancesPerEndPoint) {
        checkState(maxNumServiceInstancesPerEndPoint >= 0);

        _maxNumServiceInstancesPerEndPoint = maxNumServiceInstancesPerEndPoint;
        return this;
    }

    /**
     * Set the amount of time a cached instance is allowed to sit idle in the cache before being eligible for
     * expiration.  If never called, cached instances will not expire solely due to idle time.
     *
     * @param maxServiceInstanceIdleTime The time an instance may be idle before allowed to expire.
     * @param unit                       The unit of time the {@code maxServiceInstanceIdleTime} is in.
     * @return this
     */
    public ServiceCachingPolicyBuilder withMaxServiceInstanceIdleTime(int maxServiceInstanceIdleTime, TimeUnit unit) {
        checkState(maxServiceInstanceIdleTime > 0);
        checkNotNull(unit);

        _maxServiceInstanceIdleTimeNanos = unit.toNanos(maxServiceInstanceIdleTime);
        return this;
    }

    /**
     * Set the {@code ExhaustionAction} for the built caching policy.  If never called, will default to
     * {@code ExhaustionAction.GROW}.
     *
     * @param action The action to take when the cache is exhausted, either completely or for a requested end point.
     * @return this
     */
    public ServiceCachingPolicyBuilder withCacheExhaustionAction(ExhaustionAction action) {
        checkNotNull(action);

        _cacheExhaustionAction = action;
        return this;
    }

    /**
     * Build the {@code ServiceCachingPolicy} specified by this builder.
     *
     * @return The {@code ServiceCachingPolicy} that was constructed.
     */
    public ServiceCachingPolicy build() {
        checkState(_maxNumServiceInstances == -1 || _maxNumServiceInstancesPerEndPoint <= _maxNumServiceInstances);

        final int maxNumServiceInstances = _maxNumServiceInstances;
        final int maxNumServiceInstancesPerEndPoint = _maxNumServiceInstancesPerEndPoint;
        final long maxServiceInstanceIdleTimeNanos = _maxServiceInstanceIdleTimeNanos;
        final ExhaustionAction cacheExhaustionAction = _cacheExhaustionAction;

        return new ServiceCachingPolicy() {
            @Override
            public int getMaxNumServiceInstances() {
                return maxNumServiceInstances;
            }

            @Override
            public int getMaxNumServiceInstancesPerEndPoint() {
                return maxNumServiceInstancesPerEndPoint;
            }

            @Override
            public long getMaxServiceInstanceIdleTime(TimeUnit unit) {
                return unit.convert(maxServiceInstanceIdleTimeNanos, TimeUnit.NANOSECONDS);
            }

            @Override
            public ExhaustionAction getCacheExhaustionAction() {
                return cacheExhaustionAction;
            }
        };
    }
}
