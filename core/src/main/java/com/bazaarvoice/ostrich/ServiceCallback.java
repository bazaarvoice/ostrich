package com.bazaarvoice.ostrich;

import com.bazaarvoice.ostrich.exceptions.ServiceException;

public interface ServiceCallback<S, RETURN> {
    RETURN call(S service) throws ServiceException;
}
