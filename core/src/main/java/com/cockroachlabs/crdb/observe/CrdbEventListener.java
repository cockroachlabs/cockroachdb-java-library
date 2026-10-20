package com.cockroachlabs.crdb.observe;

/**
 * Receives operational events emitted by the library's retry and circuit-breaker
 * machinery. All methods default to no-ops so implementers override only what
 * they care about.
 */
public interface CrdbEventListener {

    /** A retryable anomaly occurred and a retry is being attempted. */
    default void onRetryAttempt(String operation, int attempt, String sqlState) {
    }

    /** Retries were exhausted for an operation. */
    default void onRetryExhausted(String operation, int attempts, String sqlState) {
    }

    /** A circuit breaker transitioned to OPEN. */
    default void onCircuitOpen(String operation, int consecutiveFailures) {
    }
}
