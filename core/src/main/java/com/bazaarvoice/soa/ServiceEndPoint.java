package com.bazaarvoice.soa;

public interface ServiceEndPoint {
    /**
     * The name of the particular group of end points that collectively provide the service.  Typically the ensemble
     * name is configured by an administrator and distinguishes a particular service ensemble from other ensembles of
     * the same service type.  For example: "production" or "test-cluster".  The ensemble name may be null if there's
     * only one ensemble of a particular service type.
     */
    String getEnsembleName();

    /**
     * The kind of the service.  Typically the service type is chosen by the service developer to distinguish the
     * service from other kinds of services.  For example: "mysql" or "image-server".
     */
    String getServiceType();

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
