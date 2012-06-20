package com.bazaarvoice.soa;

import com.bazaarvoice.soa.exceptions.ServiceException;

public interface ServiceCallback<S, RETURN> {
    RETURN call(S service) throws ServiceException;
}
