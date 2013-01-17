package com.bazaarvoice.soa.examples.dictionary.client;

import com.bazaarvoice.soa.ServiceEndPoint;
import com.bazaarvoice.soa.ServiceFactory;
import com.bazaarvoice.soa.pool.ServicePoolBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientHandlerException;
import com.sun.jersey.api.client.UniformInterfaceException;
import com.sun.jersey.client.apache4.ApacheHttpClient4;
import com.sun.jersey.client.apache4.ApacheHttpClient4Handler;
import com.sun.jersey.client.apache4.config.ApacheHttpClient4Config;
import com.sun.jersey.client.apache4.config.DefaultApacheHttpClient4Config;
import com.yammer.dropwizard.client.HttpClientConfiguration;
import com.yammer.dropwizard.client.HttpClientFactory;
import com.yammer.dropwizard.jersey.JacksonMessageBodyProvider;
import com.yammer.dropwizard.validation.Validator;
import org.apache.http.client.HttpClient;

import java.net.URI;

public class DictionaryServiceFactory implements ServiceFactory<DictionaryService> {
    private final Client _client;

    /**
     * Connects to the DictionaryService using the Apache commons http client library.
     */
    public DictionaryServiceFactory(HttpClientConfiguration configuration) {
        this(createDefaultJerseyClient(configuration));
    }

    /**
     * Connects to the DictionaryService using the specified Jersey client.  If you're writing a Dropwizard server,
     * use @{link JerseyClientFactory} to create the Jersey client.
     */
    public DictionaryServiceFactory(Client jerseyClient) {
        _client = jerseyClient;
    }

    private static ApacheHttpClient4 createDefaultJerseyClient(HttpClientConfiguration configuration) {
        HttpClient httpClient = new HttpClientFactory(configuration).build();
        ApacheHttpClient4Handler handler = new ApacheHttpClient4Handler(httpClient, null, true);
        ApacheHttpClient4Config config = new DefaultApacheHttpClient4Config();
        config.getSingletons().add(new JacksonMessageBodyProvider(new ObjectMapper(), new Validator()));
        return new ApacheHttpClient4(handler, config);
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
        // Try another server if network error (ClientHandlerException) or 5xx response code (UniformInterfaceException)
        return exception instanceof ClientHandlerException ||
                (exception instanceof UniformInterfaceException &&
                        ((UniformInterfaceException) exception).getResponse().getStatus() >= 500);
    }

    @Override
    public boolean isHealthy(ServiceEndPoint endPoint) {
        URI adminUrl = Payload.valueOf(endPoint.getPayload()).getAdminUrl();
        return _client.resource(adminUrl).path("/healthcheck").head().getStatus() == 200;
    }
}
