package com.bazaarvoice.ostrich;

/**
 * A predicate interface for {@link ServiceEndPoint} instances.
 *
 * NOTE: This interface could obviously be replaced by a Guava Predicate, but the goal is to not include any
 * 3rd party library classes in the public interface of Ostrich so that's not acceptable.
 */
public interface ServiceEndPointPredicate {
    /**
     * Apply filter.
     *
     * @param endPoint the end point
     * @return the boolean value
     */
    boolean apply(ServiceEndPoint endPoint);
}
