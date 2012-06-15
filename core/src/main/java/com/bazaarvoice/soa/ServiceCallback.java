package com.bazaarvoice.soa;

import com.bazaarvoice.soa.exceptions.ServiceException;

public interface ServiceCallback<S extends Service, RETURN> {
    RETURN call(S service) throws ServiceException;
}
