package com.bazaarvoice.ostrich.examples.dictionary.service;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;
import java.util.Optional;

@Provider
public class IllegalArgumentExceptionMapper implements ExceptionMapper<IllegalArgumentException> {
    @Override
    public Response toResponse(IllegalArgumentException e) {
        return Response.status(Response.Status.BAD_REQUEST)
                .header("X-BV-Exception", e.getClass().getName())
                .entity(Optional.ofNullable(e.getMessage()).orElse("Invalid argument."))
                .build();
    }
}
