package com.bazaarvoice.ostrich.partition;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * The interface Partition key.
 */
@Target({PARAMETER})
@Retention(RUNTIME)
public @interface PartitionKey {
    /**
     * Defines name of the property key, i.e. the key in the {@link com.bazaarvoice.ostrich.PartitionContext}.
     *
     * @return the string
     */
    String value() default "";
}
