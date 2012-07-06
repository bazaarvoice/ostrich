package com.bazaarvoice.soa.pool;

import com.bazaarvoice.soa.ServiceCallback;
import com.bazaarvoice.soa.ServiceEndPoint;
import com.bazaarvoice.soa.ServiceFactory;
import com.bazaarvoice.soa.exceptions.ServiceException;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalListener;
import com.google.common.cache.RemovalNotification;
import com.google.common.collect.ConcurrentHashMultiset;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;

import static com.google.common.base.Preconditions.checkNotNull;

class ServiceCache<S> {
    private final ConcurrentMap<S, ServiceEndPoint> _cache;
    private final ConcurrentHashMap<ServiceEndPoint, ConcurrentLinkedQueue<S>> _availableServices;
    private final ConcurrentHashMultiset<ServiceEndPoint> _endPointCounts;
    private final ServiceFactory<S> _serviceFactory;
    private final int _maxPerEndPoint;

    ServiceCache(ServiceFactory<S> serviceFactory, ServiceCachingPolicy policy) {
        checkNotNull(serviceFactory);
        _cache = CacheBuilder.newBuilder()
                            .maximumSize(policy.maxSize)
                            .expireAfterAccess(policy.maxDuration, policy.timeUnit)
                            .removalListener(new RemovalListener<S, ServiceEndPoint>() {
                                @Override
                                public void onRemoval(RemovalNotification<S, ServiceEndPoint> removalNotification) {
                                    removedFromCache(removalNotification);
                                }
                            }).<S, ServiceEndPoint>build()
                            .asMap();
        _serviceFactory = serviceFactory;
        _maxPerEndPoint = policy.maxPerEndPoint;
        _endPointCounts = ConcurrentHashMultiset.create();
        _availableServices = new ConcurrentHashMap<ServiceEndPoint, ConcurrentLinkedQueue<S>>();
    }

    <R> R call(ServiceCallback<S, R> serviceCallback, ServiceEndPoint endPoint)
            throws ServiceException {
        checkNotNull(serviceCallback);
        checkNotNull(endPoint);
        final ServiceStatus serviceStatus = checkOut(endPoint);
        try {
            return serviceCallback.call(serviceStatus.service);
        } finally {
            if (serviceStatus.shouldCheckIn) {
                checkIn(endPoint, serviceStatus.service);
            }
        }
    }

    @VisibleForTesting
    ServiceStatus checkOut(ServiceEndPoint endPoint) {
        checkNotNull(endPoint);
        S service;
        ConcurrentLinkedQueue<S> queue = _availableServices.get(endPoint);
        boolean checkedOut;
        do {
            while (queue == null) {
                _availableServices.putIfAbsent(endPoint, new ConcurrentLinkedQueue<S>());
                queue = _availableServices.get(endPoint);
            }
            if (queue.isEmpty()) {
                service = _serviceFactory.create(endPoint);
                _endPointCounts.add(endPoint);
                if (checkedOut = _endPointCounts.count(endPoint) <= _maxPerEndPoint) {
                    _cache.putIfAbsent(service, endPoint);
                } else {
                    _endPointCounts.remove(endPoint);
                }
            } else {
                service = queue.poll();
                if (!endPoint.equals(_cache.get(service))) {
                    service = null;
                }
                checkedOut = true;
            }
        } while (service == null);
        return new ServiceStatus(service, checkedOut);
    }

    private void checkIn(ServiceEndPoint endPoint, S service) {
        if (_cache.containsKey(service)) {
            _availableServices.get(endPoint).offer(service);
        }
    }

    void endPointRemoved(ServiceEndPoint endPoint) {
        checkNotNull(endPoint);
        _endPointCounts.setCount(endPoint, 0);
        Queue<S> queue = _availableServices.remove(endPoint);
        if (queue != null) {
            while (!queue.isEmpty()) {
                _cache.remove(queue.poll(), endPoint);
            }
        }
    }

    private void removedFromCache(RemovalNotification<S, ServiceEndPoint> removalNotification) {
        checkNotNull(removalNotification);
        final S service = removalNotification.getKey();
        final ServiceEndPoint endPoint = removalNotification.getValue();
        switch (removalNotification.getCause()) {
            case EXPLICIT:
                // Explicit removal cleanup should be taken care of at removal time
                break;
            case REPLACED:
                // Should never happen
                break;
            case COLLECTED:
                // Should never happen
                break;
            case EXPIRED:
                cleanUp(endPoint, service);
                break;
            case SIZE:
                cleanUp(endPoint, service);
                break;
        }
    }

    private void cleanUp(ServiceEndPoint endPoint, S service) {
        checkNotNull(endPoint);
        checkNotNull(service);
        _endPointCounts.remove(endPoint);
        if(_endPointCounts.count(endPoint) == 0) {
            Queue<S> queue = _availableServices.remove(endPoint);
            if (queue != null) {
                queue.clear();
            }
        } else {
            Queue<S> queue = _availableServices.get(endPoint);
            if (queue != null) {
                queue.remove(service);
            }
        }
    }

    private class ServiceStatus {
        final S service;
        final boolean shouldCheckIn;

        ServiceStatus(S service, boolean shouldCheckIn) {
            this.service = service;
            this.shouldCheckIn = shouldCheckIn;
        }
    }

}
