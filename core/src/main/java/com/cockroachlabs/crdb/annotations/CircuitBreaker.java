package com.cockroachlabs.crdb.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Wraps the annotated method in a circuit breaker. After {@link #failureThreshold()}
 * consecutive failures the breaker opens and fast-fails calls with
 * {@code CrdbException(CircuitOpen)} until {@link #resetTimeoutMillis()} elapses.
 */
@Documented
@Retention(RUNTIME)
@Target(METHOD)
public @interface CircuitBreaker {

    /** Consecutive failures that trip the breaker into the OPEN state. */
    int failureThreshold() default 5;

    /** Time the breaker stays OPEN before allowing a HALF_OPEN trial, in millis. */
    long resetTimeoutMillis() default 30000;
}
