package com.bazaarvoice.soa.examples.dictionary.service;

import com.bazaarvoice.soa.examples.dictionary.client.WordRange;
import com.yammer.metrics.annotation.Timed;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * A Dropwizard+Jersey-based RESTful implementation of a simple dictionary service.
 * <p>
 * Note: the url contains the service name "dictionary" to allow multiple services to be hosted on the same server.
 * It contains the version number "1" to allow multiple versions of the same service to be hosted on the same server.
 * Future backward-incompatible versions of the dictionary API can be hosted at "/dictionary/2", ....
 * <p>
 * Contains a backdoor for simulating server outages.
 */
@Path("/dictionary/1")
@Produces(MediaType.APPLICATION_JSON)
public class DictionaryResource {
    private final WordList _words;
    private final WordRange _range;

    public DictionaryResource(WordList words, WordRange range) {
        _words = words;
        _range = range;
    }

    @GET
    @Timed
    @Path("/contains/{word}")
    public boolean contains(@PathParam("word") String word) {
        checkHealthy();
        checkArgument(_range.apply(word), "Word does not belong to the range handled by this server: %s", word);

        return _words.apply(word);
    }

    private void checkHealthy() {
        // Simulate a server failure.  Clients should attempt to failover to another server.
        // They will retry or not based on the status code range.
        if (DictionaryService.STATUS_OVERRIDE != Response.Status.OK) {
            throw new WebApplicationException(DictionaryService.STATUS_OVERRIDE);
        }
    }
}
