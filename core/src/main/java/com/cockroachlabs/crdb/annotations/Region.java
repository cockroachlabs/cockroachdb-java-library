package com.cockroachlabs.crdb.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Pins the annotated method's execution to a CockroachDB region. The value is
 * placed in {@link com.cockroachlabs.crdb.context.CrdbContext} for the duration
 * of the call (useful for REGIONAL BY ROW access patterns).
 */
@Documented
@Retention(RUNTIME)
@Target(METHOD)
public @interface Region {

    /** The region name (e.g. {@code us-east1}). */
    String value();
}
