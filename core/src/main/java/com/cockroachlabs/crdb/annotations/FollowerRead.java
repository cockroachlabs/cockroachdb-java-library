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
 * low-latency, slightly-stale results. A plain {@code @FollowerRead} always does
 * a follower read; there is no fallback to a current read.
 *
 * <p>With nothing set (the default), it reads at {@code follower_read_timestamp()},
 * the most recent time any replica can serve (about 4.2s ago). Otherwise the
 * {@code hoursAgo}/{@code minutesAgo}/{@code secondsAgo} parts add up, so
 * {@code @FollowerRead(hoursAgo = 1, minutesAgo = 30)} reads as of 1h30m ago
 * ({@code AS OF SYSTEM TIME '-5400s'}). If any of those parts is set, they take
 * precedence over {@link #staleness()}. A negative part fails fast when the
 * annotated method is called.
 */
@Documented
@Retention(RUNTIME)
@Target(METHOD)
public @interface FollowerRead {

    /**
     * Raw staleness expression, inlined after {@code AS OF SYSTEM TIME}. Used
     * only when {@code hoursAgo}/{@code minutesAgo}/{@code secondsAgo} are all 0.
     * Defaults to CockroachDB's exact-staleness helper
     * {@code follower_read_timestamp()}.
     */
    String staleness() default "follower_read_timestamp()";

    /** Hours of staleness. Added to {@code minutesAgo} and {@code secondsAgo}. */
    long hoursAgo() default 0;

    /** Minutes of staleness. Added to {@code hoursAgo} and {@code secondsAgo}. */
    long minutesAgo() default 0;

    /** Seconds of staleness. Added to {@code hoursAgo} and {@code minutesAgo}. */
    long secondsAgo() default 0;
}
