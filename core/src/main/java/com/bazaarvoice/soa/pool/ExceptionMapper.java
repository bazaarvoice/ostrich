package com.bazaarvoice.soa.pool;

import com.bazaarvoice.soa.exceptions.ServiceException;

/**
 * Maps exceptions from one type to another.  In general this is useful for translating from transport-specific
 * exception types to application exception types.  It can also be used to wrap an exception type with a
 * {@link ServiceException} which makes a service pool operation retryable.
 */
public interface ExceptionMapper {

    /**
     * Convert an exception from one type to another.
     */
    Throwable translate(Throwable t);
}
