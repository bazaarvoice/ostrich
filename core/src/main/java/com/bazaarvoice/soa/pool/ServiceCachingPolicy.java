package com.bazaarvoice.soa.pool;

import java.util.concurrent.TimeUnit;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * A policy for determining connection caching performed by  a {@link ServicePool}. A {@code ServiceCachingPolicy}
 * configures how many total connections can be held simultaneously for a service, how many can be held for a single
 * end point, how long a connection can sit idle before being eligible to be cleaned up, and how frequently culling
 * those idle connections can happen. Note that there is no guaranteed eviction time, so a connection can potentially
 * remain cached and idle for as long as {code minConnectionIdleTimeBeforeEviction + idleConnectionEvictionFrequency}.
 */
public final class ServiceCachingPolicy {
    /** The maximum total number of cached connections for a service. */
    final long _maxTotalConnections;

    /** The maximum number of cached connections for a single {@link com.bazaarvoice.soa.ServiceEndPoint}. */
    final long _maxConnectionsPerEndPoint;

    /** The minimum amount of time a connection must be idle before it is eligible for eviction. */
    final long _minConnectionIdleTimeBeforeEviction;

    /** The frequency with which idle connections are checked for eviction eligibility. */
    final long _idleConnectionEvictionFrequency;

    /** The {@code TimeUnit} for {@link #_minConnectionIdleTimeBeforeEviction} and
     * {@link #_idleConnectionEvictionFrequency}.
     * */
    final TimeUnit _unit;

    /**
     * Constructs a caching policy. All configuration options must be specified, as sensible values depend highly on the
     * constraints of the application.
     * @param maxTotalConnections The maximum number of connections that should be cached, aggregated over all end
     * points.
     * @param maxConnectionsPerEndPoint The maximum number of connections cached for a single end point.
     * @param minConnectionIdleTimeBeforeEviction The minimum amount of time that should base before a cached connection
     * is considered for eviction. As this is a minimum, there is no guarantee that a cached connection will expire
     * precisely when it has been idle for this long.
     * @param idleConnectionEvictionFrequency How frequently cached connections should be checked for eviction.
     * @param unit The time unit {@code minConnectionIdleTimeBeforeEviction} and {@code idleConnectionEvictionFrequency}
     * are in.
     */
    public ServiceCachingPolicy(int maxTotalConnections, int maxConnectionsPerEndPoint,
                                long minConnectionIdleTimeBeforeEviction, long idleConnectionEvictionFrequency,
                                TimeUnit unit) {
        checkArgument(maxTotalConnections > 0);
        checkArgument(maxConnectionsPerEndPoint > 0);
        checkArgument(minConnectionIdleTimeBeforeEviction >= 0);
        checkArgument(idleConnectionEvictionFrequency > 0);
        checkNotNull(unit);

        _maxTotalConnections = maxTotalConnections;
        _maxConnectionsPerEndPoint = maxConnectionsPerEndPoint;
        _minConnectionIdleTimeBeforeEviction = minConnectionIdleTimeBeforeEviction;
        _idleConnectionEvictionFrequency = idleConnectionEvictionFrequency;
        _unit = unit;
    }
}
