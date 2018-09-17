package com.bazaarvoice.ostrich.examples.dictionary.client;

import com.bazaarvoice.ostrich.ServiceEndPoint;
import com.bazaarvoice.ostrich.ServiceFactory;
import com.bazaarvoice.ostrich.pool.ServicePoolBuilder;
import com.codahale.metrics.MetricRegistry;
import io.dropwizard.client.JerseyClientBuilder;
import io.dropwizard.client.JerseyClientConfiguration;

import javax.ws.rs.ProcessingException;
import javax.ws.rs.ServerErrorException;
import javax.ws.rs.client.Client;
import javax.ws.rs.core.Response;
import java.net.URI;

public class DictionaryServiceFactory implements ServiceFactory<DictionaryService> {
    private final Client _client;

    /**
     * Connects to the DictionaryService using the Apache commons http client library.
     */
    public DictionaryServiceFactory(JerseyClientConfiguration configuration, MetricRegistry metrics) {
        this(createDefaultJerseyClient(configuration, metrics));
    }

    /**
     * Connects to the DictionaryService using the specified Jersey client.  If you're writing a Dropwizard server,
     * use @{link JerseyClientFactory} to create the Jersey client.
     */
    public DictionaryServiceFactory(Client jerseyClient) {
        _client = jerseyClient;
    }

    private static Client createDefaultJerseyClient(JerseyClientConfiguration configuration,
                                                               MetricRegistry metrics) {
        return new JerseyClientBuilder(metrics).using(configuration).build("dictionary");
    }

    @Override
    public String getServiceName() {
        return "dictionary";
    }

    @Override
    public void configure(ServicePoolBuilder<DictionaryService> servicePoolBuilder) {
        // Set up partitioning on the builder.
        servicePoolBuilder.withPartitionFilter(new DictionaryPartitionFilter())
                .withPartitionContextAnnotationsFrom(DictionaryClient.class);
    }

    @Override
    public DictionaryService create(ServiceEndPoint endPoint) {
        return new DictionaryClient(endPoint, _client);
    }

    @Override
    public void destroy(ServiceEndPoint endPoint, DictionaryService service) {
        // We don't need to do any cleanup.
    }

    @Override
    public boolean isRetriableException(Exception exception) {
        // Try another server if network error or parsing error (ProcessingException)
        // or 5xx response code (ServerErrorException)
        return exception instanceof ProcessingException || exception instanceof ServerErrorException;
    }

    @Override
    public boolean isHealthy(ServiceEndPoint endPoint) {
        URI adminUrl = Payload.valueOf(endPoint.getPayload()).getAdminUrl();
        Response response = _client.target(adminUrl).path("/healthcheck").request().method("HEAD");
        int status = response.getStatus();
        response.close();
        return status == Response.Status.OK.getStatusCode();
    }
}
