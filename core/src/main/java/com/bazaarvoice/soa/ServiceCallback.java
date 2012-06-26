package com.bazaarvoice.soa;

import com.bazaarvoice.soa.exceptions.ServiceException;

public interface ServiceCallback<S, RETURN> {

    @SuppressWarnings("DuplicateThrows")
    RETURN call(S service) throws ServiceException, Exception;
}
