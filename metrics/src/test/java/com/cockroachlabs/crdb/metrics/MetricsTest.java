package com.cockroachlabs.crdb.metrics;

import com.cockroachlabs.crdb.observe.CrdbEvents;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.MeterProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MetricsTest {

    @AfterEach
    void cleanup() {
        CrdbEvents.clearListeners();
    }

    @Test
    void inMemoryRecorderCountsTransactionsAndDurations() {
        InMemoryMetricsRecorder r = new InMemoryMetricsRecorder();
        r.recordTransaction(Duration.ofMillis(10), 0, true);
        r.recordTransaction(Duration.ofMillis(30), 2, false);

        MetricsSnapshot snap = r.snapshot();
        assertEquals(2, snap.transactionCount());
        assertEquals(1, snap.transactionSuccessCount());
        assertEquals(1, snap.transactionFailureCount());
        assertEquals(Duration.ofMillis(40), snap.totalTransactionDuration());
        assertEquals(Duration.ofMillis(20), snap.meanTransactionDuration());
    }

    @Test
    void resetZeroesCounters() {
        InMemoryMetricsRecorder r = new InMemoryMetricsRecorder();
        r.recordTransaction(Duration.ofMillis(5), 0, true);
        r.recordRetryAttempt("op", 1, "40001");
        r.reset();

        MetricsSnapshot snap = r.snapshot();
        assertEquals(0, snap.transactionCount());
        assertEquals(0, snap.retryAttempts());
        assertEquals(Duration.ZERO, snap.totalTransactionDuration());
    }

    @Test
    void listenerFeedsRecorderFromObserveBus() {
        InMemoryMetricsRecorder recorder = new InMemoryMetricsRecorder();
        CrdbMetrics metrics = new CrdbMetrics(recorder).register();
        try {
            // Simulate what the core retry/circuit machinery emits.
            CrdbEvents.fireRetryAttempt("txn", 1, "40001");
            CrdbEvents.fireRetryAttempt("txn", 2, "40001");
            CrdbEvents.fireRetryExhausted("txn", 3, "40001");
            CrdbEvents.fireCircuitOpen("txn", 5);

            MetricsSnapshot snap = recorder.snapshot();
            assertEquals(2, snap.retryAttempts());
            assertEquals(1, snap.retriesExhausted());
            assertEquals(1, snap.circuitOpens());
        } finally {
            metrics.unregister();
        }
    }

    @Test
    void unregisterStopsCounting() {
        InMemoryMetricsRecorder recorder = new InMemoryMetricsRecorder();
        CrdbMetrics metrics = new CrdbMetrics(recorder).register();
        metrics.unregister();

        CrdbEvents.fireRetryAttempt("txn", 1, "40001");
        assertEquals(0, recorder.snapshot().retryAttempts());
    }

    @Test
    void timeTransactionRecordsSuccessAndFailure() {
        InMemoryMetricsRecorder recorder = new InMemoryMetricsRecorder();
        CrdbMetrics metrics = new CrdbMetrics(recorder);

        String out = metrics.timeTransaction(() -> "ok");
        assertEquals("ok", out);

        assertThrows(IllegalStateException.class,
                () -> metrics.timeTransaction(() -> {
                    throw new IllegalStateException("boom");
                }));

        MetricsSnapshot snap = recorder.snapshot();
        assertEquals(2, snap.transactionCount());
        assertEquals(1, snap.transactionSuccessCount());
        assertEquals(1, snap.transactionFailureCount());
        assertTrue(snap.totalTransactionDuration().toNanos() >= 0);
    }

    @Test
    void otelRecorderAcceptsAllSignals() {
        Meter meter = MeterProvider.noop().get("cockroachdb-java-library-test");
        OtelMetricsRecorder r = new OtelMetricsRecorder(meter);
        // Exercise every path; the no-op meter must not throw.
        r.recordTransaction(Duration.ofMillis(12), 1, true);
        r.recordTransaction(Duration.ofMillis(3), 0, false);
        r.recordRetryAttempt("op", 1, "40001");
        r.recordRetriesExhausted("op", 3, "40001");
        r.recordCircuitOpen("op", 4);
    }
}
