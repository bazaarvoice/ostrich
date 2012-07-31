package com.bazaarvoice.soa;

/**
 * A predicate interface for {@link ServiceEndPoint} instances.
 * <p/>
 * NOTE: This interface could obviously be replaced by a Guava Predicate, but the goal is to not include any
 * 3rd party library classes in the public interface of Ostrich so that's not acceptable.
 */
public interface ServiceEndPointPredicate {
    boolean apply(ServiceEndPoint endPoint);
}
