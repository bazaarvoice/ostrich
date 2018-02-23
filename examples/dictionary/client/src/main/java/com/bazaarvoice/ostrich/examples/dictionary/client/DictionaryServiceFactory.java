package com.bazaarvoice.ostrich.examples.dictionary.client;

import com.bazaarvoice.ostrich.ServiceEndPoint;
import com.bazaarvoice.ostrich.ServiceFactory;
import com.bazaarvoice.ostrich.pool.ServicePoolBuilder;
import com.codahale.metrics.MetricRegistry;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientHandlerException;
import com.sun.jersey.api.client.UniformInterfaceException;
import com.sun.jersey.client.apache4.ApacheHttpClient4;
import com.sun.jersey.client.apache4.ApacheHttpClient4Handler;
import com.sun.jersey.client.apache4.config.ApacheHttpClient4Config;
import com.sun.jersey.client.apache4.config.DefaultApacheHttpClient4Config;
import io.dropwizard.client.HttpClientBuilder;
import io.dropwizard.client.HttpClientConfiguration;
import io.dropwizard.jackson.Jackson;
import io.dropwizard.jersey.jackson.JacksonMessageBodyProvider;
import org.apache.http.client.HttpClient;

import javax.validation.Validation;
import javax.validation.Validator;
import java.net.URI;

/**
 * The type Dictionary service factory.
 */
public class DictionaryServiceFactory implements ServiceFactory<DictionaryService> {
    private final Client _client;

    /**
     * Connects to the DictionaryService using the Apache commons http client library.
     *
     * @param configuration the configuration
     * @param metrics       the metrics
     */
    public DictionaryServiceFactory(HttpClientConfiguration configuration, MetricRegistry metrics) {
        this(createDefaultJerseyClient(configuration, metrics));
    }

    /**
     * Connects to the DictionaryService using the specified Jersey client.  If you're writing a Dropwizard server,
     * use @{link JerseyClientFactory} to create the Jersey client.
     *
     * @param jerseyClient the jersey client
     */
    public DictionaryServiceFactory(Client jerseyClient) {
        _client = jerseyClient;
    }

    private static ApacheHttpClient4 createDefaultJerseyClient(HttpClientConfiguration configuration,
                                                               MetricRegistry metrics) {
        HttpClient httpClient = new HttpClientBuilder(metrics).using(configuration).build("dictionary");
        ApacheHttpClient4Handler handler = new ApacheHttpClient4Handler(httpClient, null, true);
        ApacheHttpClient4Config config = new DefaultApacheHttpClient4Config();
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        config.getSingletons().add(new JacksonMessageBodyProvider(Jackson.newObjectMapper(), validator));
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
