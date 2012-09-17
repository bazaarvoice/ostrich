package com.bazaarvoice.soa.examples.dictionary.service;

import com.google.common.base.Objects;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

@Provider
public class IllegalArgumentExceptionMapper implements ExceptionMapper<IllegalArgumentException> {
    @Override
    public Response toResponse(IllegalArgumentException e) {
        return Response.status(Response.Status.BAD_REQUEST)
                .header("X-BV-Exception", e.getClass().getName())
                .entity(Objects.firstNonNull(e.getMessage(), "Invalid argument."))
                .build();
    }
}
