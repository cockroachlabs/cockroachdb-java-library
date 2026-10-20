package com.cockroachlabs.crdb.metrics;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;

import java.time.Duration;

/**
 * A {@link MetricsRecorder} that exports to OpenTelemetry through a
 * {@link Meter}. Instrument names are shared across the sibling libraries so a
 * single dashboard works regardless of the implementation language.
 *
 * <p>Instruments:
 * <ul>
 *   <li>{@code cockroachdb.transactions.total} (counter, attribute {@code success})</li>
 *   <li>{@code cockroachdb.transactions.duration} (histogram, ms)</li>
 *   <li>{@code cockroachdb.retries.attempts} (counter)</li>
 *   <li>{@code cockroachdb.retries.exhausted} (counter)</li>
 *   <li>{@code cockroachdb.circuit.opens} (counter)</li>
 * </ul>
 *
 * <p>Obtain the {@link Meter} from your application's {@code MeterProvider}, e.g.
 * {@code GlobalOpenTelemetry.getMeter("cockroachdb-java-library")}.
 */
public final class OtelMetricsRecorder implements MetricsRecorder {

    private static final AttributeKey<Boolean> SUCCESS = AttributeKey.booleanKey("success");

    private final LongCounter transactionTotal;
    private final DoubleHistogram transactionDuration;
    private final LongCounter retryAttempts;
    private final LongCounter retriesExhausted;
    private final LongCounter circuitOpens;

    public OtelMetricsRecorder(Meter meter) {
        this.transactionTotal = meter.counterBuilder("cockroachdb.transactions.total")
                .setDescription("Total CockroachDB transactions")
                .build();
        this.transactionDuration = meter.histogramBuilder("cockroachdb.transactions.duration")
                .setDescription("CockroachDB transaction duration")
                .setUnit("ms")
                .build();
        this.retryAttempts = meter.counterBuilder("cockroachdb.retries.attempts")
                .setDescription("Retry attempts on retryable anomalies")
                .build();
        this.retriesExhausted = meter.counterBuilder("cockroachdb.retries.exhausted")
                .setDescription("Operations that exhausted their retry budget")
                .build();
        this.circuitOpens = meter.counterBuilder("cockroachdb.circuit.opens")
                .setDescription("Circuit-breaker OPEN transitions")
                .build();
    }

    @Override
    public void recordTransaction(Duration duration, int retries, boolean success) {
        Attributes attrs = Attributes.of(SUCCESS, success);
        transactionTotal.add(1, attrs);
        if (duration != null) {
            transactionDuration.record(duration.toNanos() / 1_000_000.0, attrs);
        }
    }

    @Override
    public void recordRetryAttempt(String operation, int attempt, String sqlState) {
        retryAttempts.add(1);
    }

    @Override
    public void recordRetriesExhausted(String operation, int attempts, String sqlState) {
        retriesExhausted.add(1);
    }

    @Override
    public void recordCircuitOpen(String operation, int consecutiveFailures) {
        circuitOpens.add(1);
    }
}
