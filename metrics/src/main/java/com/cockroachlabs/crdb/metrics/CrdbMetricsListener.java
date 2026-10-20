package com.cockroachlabs.crdb.metrics;

import com.cockroachlabs.crdb.observe.CrdbEventListener;

import java.util.Objects;

/**
 * Bridges the core {@code observe} event bus into a {@link MetricsRecorder}.
 *
 * <p>Register it with {@code CrdbEvents.addListener(...)} — or, more simply, use
 * {@link CrdbMetrics#register()} — and every retry attempt, exhausted retry, and
 * circuit-open transition emitted anywhere in the library is counted
 * automatically, with no changes to application code.
 */
public final class CrdbMetricsListener implements CrdbEventListener {

    private final MetricsRecorder recorder;

    public CrdbMetricsListener(MetricsRecorder recorder) {
        this.recorder = Objects.requireNonNull(recorder, "recorder");
    }

    @Override
    public void onRetryAttempt(String operation, int attempt, String sqlState) {
        recorder.recordRetryAttempt(operation, attempt, sqlState);
    }

    @Override
    public void onRetryExhausted(String operation, int attempts, String sqlState) {
        recorder.recordRetriesExhausted(operation, attempts, sqlState);
    }

    @Override
    public void onCircuitOpen(String operation, int consecutiveFailures) {
        recorder.recordCircuitOpen(operation, consecutiveFailures);
    }
}
