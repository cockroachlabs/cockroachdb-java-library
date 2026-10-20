package com.cockroachlabs.crdb.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Marks the annotated method as a follower-read call. The proxy engine sets the
 * follower-read staleness in {@link com.cockroachlabs.crdb.context.CrdbContext},
 * so reads run with {@code AS OF SYSTEM TIME} against the nearest replica for
 * low-latency, slightly-stale results.
 */
@Documented
@Retention(RUNTIME)
@Target(METHOD)
public @interface FollowerRead {

    /**
     * Staleness expression. Defaults to CockroachDB's exact-staleness helper
     * {@code follower_read_timestamp()}.
     */
    String staleness() default "follower_read_timestamp()";
}
