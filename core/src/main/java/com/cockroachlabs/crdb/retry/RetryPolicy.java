package com.cockroachlabs.crdb.retry;

/**
 * Immutable retry configuration.
 *
 * @param maxRetries      maximum retry attempts (in addition to the first try)
 * @param baseDelayMillis base backoff delay in milliseconds
 * @param maxDelayMillis  upper bound on any single delay in milliseconds
 * @param multiplier      exponential growth factor per attempt (&gt;= 1.0)
 * @param jitter          fraction of the delay applied as random jitter, in [0, 1]
 */
public record RetryPolicy(
        int maxRetries,
        long baseDelayMillis,
        long maxDelayMillis,
        double multiplier,
        double jitter) {

    public RetryPolicy {
        if (maxRetries < 0) {
            throw new IllegalArgumentException("maxRetries must be >= 0");
        }
        if (baseDelayMillis < 0 || maxDelayMillis < 0) {
            throw new IllegalArgumentException("delays must be >= 0");
        }
        if (multiplier < 1.0) {
            throw new IllegalArgumentException("multiplier must be >= 1.0");
        }
        if (jitter < 0.0 || jitter > 1.0) {
            throw new IllegalArgumentException("jitter must be in [0, 1]");
        }
    }

    /** Sensible defaults: 5 retries, 100ms base, 5s cap, x2 backoff, 20% jitter. */
    public static RetryPolicy defaults() {
        return new RetryPolicy(5, 100L, 5000L, 2.0, 0.2);
    }
}
