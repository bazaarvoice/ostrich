package com.bazaarvoice.ostrich.pool;

import com.bazaarvoice.ostrich.ServiceEndPoint;

import static com.google.common.base.Preconditions.checkNotNull;

class ServiceHandle<S> {
    private final S _service;
    private final ServiceEndPoint _endPoint;

    public ServiceHandle(S service, ServiceEndPoint endPoint) {
        _service = checkNotNull(service);
        _endPoint = checkNotNull(endPoint);
    }

    public S getService() {
        return _service;
    }

    ServiceEndPoint getEndPoint() {
        return _endPoint;
    }
}
