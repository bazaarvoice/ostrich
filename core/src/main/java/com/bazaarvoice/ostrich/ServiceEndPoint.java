package com.bazaarvoice.ostrich;

public interface ServiceEndPoint {
    /** The name of the service. */
    String getServiceName();

    /**
     * An opaque identifier for this end point.
     * <p/>
     * The format of this identifier and information (if any) contained within it is application specific.  Ostrich
     * does not introspect into this at all.
     */
    String getId();

    /** An optional payload provided by the user that registered the service. */
    String getPayload();
}
