package com.cockroachlabs.crdb.metrics;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A thread-safe {@link MetricsRecorder} backed by atomic counters. Suitable for
 * tests, development, and lightweight production use where a full telemetry
 * pipeline is unnecessary.
 *
 * <p>Read accumulated values with {@link #snapshot()} and zero them with
 * {@link #reset()}.
 */
public final class InMemoryMetricsRecorder implements MetricsRecorder {

    private final AtomicLong transactionCount = new AtomicLong();
    private final AtomicLong transactionSuccessCount = new AtomicLong();
    private final AtomicLong transactionFailureCount = new AtomicLong();
    private final AtomicLong retryAttempts = new AtomicLong();
    private final AtomicLong retriesExhausted = new AtomicLong();
    private final AtomicLong circuitOpens = new AtomicLong();
    private final AtomicLong totalTransactionNanos = new AtomicLong();

    @Override
    public void recordTransaction(Duration duration, int retries, boolean success) {
        transactionCount.incrementAndGet();
        if (success) {
            transactionSuccessCount.incrementAndGet();
        } else {
            transactionFailureCount.incrementAndGet();
        }
        if (duration != null) {
            totalTransactionNanos.addAndGet(duration.toNanos());
        }
    }

    @Override
    public void recordRetryAttempt(String operation, int attempt, String sqlState) {
        retryAttempts.incrementAndGet();
    }

    @Override
    public void recordRetriesExhausted(String operation, int attempts, String sqlState) {
        retriesExhausted.incrementAndGet();
    }

    @Override
    public void recordCircuitOpen(String operation, int consecutiveFailures) {
        circuitOpens.incrementAndGet();
    }

    /** A point-in-time copy of all counters. */
    public MetricsSnapshot snapshot() {
        return new MetricsSnapshot(
                transactionCount.get(),
                transactionSuccessCount.get(),
                transactionFailureCount.get(),
                retryAttempts.get(),
                retriesExhausted.get(),
                circuitOpens.get(),
                Duration.ofNanos(totalTransactionNanos.get()));
    }

    /** Zero all counters. Useful between test cases. */
    public void reset() {
        transactionCount.set(0);
        transactionSuccessCount.set(0);
        transactionFailureCount.set(0);
        retryAttempts.set(0);
        retriesExhausted.set(0);
        circuitOpens.set(0);
        totalTransactionNanos.set(0);
    }
}
