package com.cockroachlabs.crdb.annotations;

import com.cockroachlabs.crdb.tx.Isolation;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Runs the annotated method inside a JDBC transaction (SERIALIZABLE by default).
 *
 * <p>When {@link #retry()} is {@code true} (the default) the transaction is
 * retried on serialization conflicts using CockroachDB's
 * {@code SAVEPOINT cockroach_restart} semantics, so callers never write the
 * classic {@code 40001} retry loop by hand.
 */
@Documented
@Retention(RUNTIME)
@Target(METHOD)
public @interface Transactional {

    /** Retry the transaction on serialization conflicts. */
    boolean retry() default true;

    /** Transaction isolation level. Defaults to SERIALIZABLE. */
    Isolation isolation() default Isolation.SERIALIZABLE;
}
