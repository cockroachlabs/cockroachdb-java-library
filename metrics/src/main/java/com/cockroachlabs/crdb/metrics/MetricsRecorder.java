package com.cockroachlabs.crdb.metrics;

import java.time.Duration;

/**
 * Sink for CockroachDB operational metrics.
 *
 * <p>The transaction methods are called by application code (directly or through
 * {@link CrdbMetrics#timeTransaction}); the retry and circuit methods are driven
 * automatically by {@link CrdbMetricsListener}, which forwards the core
 * {@code observe} event bus into this recorder.
 *
 * <p>All methods have empty defaults so an implementation overrides only the
 * signals it cares about. Implementations must be thread-safe: events fire from
 * arbitrary application threads.
 */
public interface MetricsRecorder {

    /**
     * Record a completed transaction.
     *
     * @param duration wall-clock duration of the transaction (including retries)
     * @param retries  number of retries used (0 if it committed on first try)
     * @param success  whether the transaction ultimately committed
     */
    default void recordTransaction(Duration duration, int retries, boolean success) {
    }

    /** A retryable anomaly occurred and a retry is being attempted. */
    default void recordRetryAttempt(String operation, int attempt, String sqlState) {
    }

    /** Retries were exhausted for an operation. */
    default void recordRetriesExhausted(String operation, int attempts, String sqlState) {
    }

    /** A circuit breaker transitioned to OPEN. */
    default void recordCircuitOpen(String operation, int consecutiveFailures) {
    }

    /** A recorder that discards everything. */
    static MetricsRecorder noop() {
        return new MetricsRecorder() {
        };
    }
}
