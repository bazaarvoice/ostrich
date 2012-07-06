package com.bazaarvoice.soa.pool;

import java.util.concurrent.TimeUnit;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public class ServiceCachingPolicy {

    final long maxSize;
    final long maxDuration;
    final TimeUnit timeUnit;
    final int maxPerEndPoint;

    public ServiceCachingPolicy(long maximumSize, long timeout, TimeUnit timeOutUnit, int maxCachedPerEndPoint) {
        checkArgument(maximumSize >= 0);
        checkArgument(timeout >= 0);
        checkNotNull(timeOutUnit);
        checkArgument(maxCachedPerEndPoint >= 0);
        maxSize = maximumSize;
        maxDuration = timeout;
        timeUnit = timeOutUnit;
        maxPerEndPoint = maxCachedPerEndPoint;
    }
}
