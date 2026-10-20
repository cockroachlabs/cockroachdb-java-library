package com.cockroachlabs.crdb.metrics;

import java.time.Duration;

/**
 * A point-in-time copy of accumulated CockroachDB operational metrics.
 *
 * @param transactionCount        total transactions recorded
 * @param transactionSuccessCount transactions that committed successfully
 * @param transactionFailureCount transactions that failed
 * @param retryAttempts           total retry attempts observed (from the core
 *                                {@code observe} event bus)
 * @param retriesExhausted        operations that exhausted their retry budget
 * @param circuitOpens            circuit-breaker OPEN transitions
 * @param totalTransactionDuration cumulative wall-clock time across all recorded
 *                                transactions
 */
public record MetricsSnapshot(
        long transactionCount,
        long transactionSuccessCount,
        long transactionFailureCount,
        long retryAttempts,
        long retriesExhausted,
        long circuitOpens,
        Duration totalTransactionDuration) {

    /** An all-zero snapshot. */
    public static MetricsSnapshot empty() {
        return new MetricsSnapshot(0, 0, 0, 0, 0, 0, Duration.ZERO);
    }

    /**
     * Mean transaction duration, or {@link Duration#ZERO} when no transactions
     * have been recorded.
     */
    public Duration meanTransactionDuration() {
        if (transactionCount <= 0) {
            return Duration.ZERO;
        }
        return totalTransactionDuration.dividedBy(transactionCount);
    }
}
