package com.bazaarvoice.ostrich;

/**
 * The interface Service end point.
 */
public interface ServiceEndPoint {
    /**
     * The name of the service.
     * @return the service name
     */
    String getServiceName();

    /**
     * An opaque identifier for this end point.
     *
     * The format of this identifier and information (if any) contained within it is application specific.  Ostrich
     * does not introspect into this at all.
     *
     * @return the id
     */
    String getId();

    /**
     * An optional payload provided by the user that registered the service.
     * @return the payload
     */
    String getPayload();
}
