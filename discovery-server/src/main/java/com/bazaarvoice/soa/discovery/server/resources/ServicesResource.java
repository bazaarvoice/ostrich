package com.bazaarvoice.soa.discovery.server.resources;

import com.bazaarvoice.soa.HostDiscovery;
import com.bazaarvoice.soa.ServiceEndPoint;
import com.bazaarvoice.soa.discovery.ZooKeeperServiceDiscovery;
import com.bazaarvoice.soa.discovery.server.ZooKeeperHostDiscoveryFactory;
import com.google.inject.Inject;
import com.yammer.metrics.annotation.Timed;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import static com.google.common.base.Preconditions.checkNotNull;

@Path("/services")
@Produces(MediaType.APPLICATION_JSON)
public class ServicesResource {
    private final ZooKeeperHostDiscoveryFactory _factory;
    private final ZooKeeperServiceDiscovery _serviceDiscovery;

    @Inject
    ServicesResource(ZooKeeperHostDiscoveryFactory factory, ZooKeeperServiceDiscovery serviceDiscovery) {
        _factory = checkNotNull(factory);
        _serviceDiscovery = checkNotNull(serviceDiscovery);
    }

    /** Return a list of all of the available services. */
    @GET
    @Timed
    public Iterable<String> getServiceNames() {
        return _serviceDiscovery.getServices();
    }

    /** Return a list of the endpoints that are currently available for the provided service. */
    @Path("/{service}/endpoints")
    @GET
    @Timed
    public Iterable<ServiceEndPoint> getEndPoints(@PathParam("service") String serviceName) {
        HostDiscovery discovery = _factory.getHostDiscovery(serviceName);
        return discovery.getHosts();
    }
}
