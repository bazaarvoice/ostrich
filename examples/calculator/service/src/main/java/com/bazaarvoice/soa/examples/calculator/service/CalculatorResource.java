package com.bazaarvoice.soa.examples.calculator.service;

import com.yammer.metrics.annotation.Timed;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

/**
 * A Dropwizard+Jersey-based RESTful implementation of a simple calculator service.
 * <p>
 * Note: the url contains the service name "calculator" to allow multiple services to be hosted on the same server.
 * It contains the version number "1" to allow multiple versions of the same service to be hosted on the same server.
 * Future backward-incompatible versions of the calculator API can be hosted at "/calculator/2", ....
 * <p>
 * Contains a backdoor for simulating server outages.
 */
@Path("/calculator/1")
@Produces(MediaType.APPLICATION_JSON)
public class CalculatorResource {
    @GET
    @Timed
    @Path("/add/{arg1}/{arg2}")
    public int add(@PathParam("arg1") int arg1, @PathParam("arg2") int arg2) {
        checkHealthy();
        return arg1 + arg2;
    }

    @GET
    @Timed
    @Path("/sub/{arg1}/{arg2}")
    public int sub(@PathParam("arg1") int arg1, @PathParam("arg2") int arg2) {
        checkHealthy();
        return arg1 - arg2;
    }

    @GET
    @Timed
    @Path("/mul/{arg1}/{arg2}")
    public int mul(@PathParam("arg1") int arg1, @PathParam("arg2") int arg2) {
        checkHealthy();
        return arg1 * arg2;
    }

    @GET
    @Timed
    @Path("/div/{arg1}/{arg2}")
    public int div(@PathParam("arg1") int arg1, @PathParam("arg2") int arg2) {
        checkHealthy();
        return arg1 / arg2;
    }

    private void checkHealthy() {
        // Simulate a server failure.  Clients should attempt to failover to another server.
        // They will retry or not based on the status code range.
        if (CalculatorService.STATUS_OVERRIDE != Response.Status.OK) {
            throw new WebApplicationException(CalculatorService.STATUS_OVERRIDE);
        }
    }
}
