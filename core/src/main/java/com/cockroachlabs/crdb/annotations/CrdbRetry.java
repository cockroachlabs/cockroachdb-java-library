package com.cockroachlabs.crdb.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Retries the annotated method on CockroachDB serialization anomalies
 * (SQLSTATE {@code 40001} / {@code 40P01}) using adaptive exponential backoff.
 *
 * <p>Unlike {@link Transactional}, this annotation does not open a transaction;
 * it simply re-invokes the method body. Combine with {@link Transactional} for
 * transactional retry, or use standalone around an already-transactional unit.
 */
@Documented
@Retention(RUNTIME)
@Target(METHOD)
public @interface CrdbRetry {

    /** Maximum number of retry attempts before giving up. */
    int maxRetries() default 5;

    /** Base delay between attempts, in milliseconds. */
    long baseDelayMillis() default 100;

    /** Upper bound on any single backoff delay, in milliseconds. */
    long maxDelayMillis() default 5000;
}
