/**
 * Metrics and OpenTelemetry instrumentation for CockroachDB operations.
 *
 * <p>{@link com.cockroachlabs.crdb.metrics.MetricsRecorder} is the sink;
 * {@link com.cockroachlabs.crdb.metrics.InMemoryMetricsRecorder} counts
 * transactions, retries, exhausted retries, circuit-opens, and durations with
 * atomic counters, and {@link com.cockroachlabs.crdb.metrics.OtelMetricsRecorder}
 * exports the same signals through an OpenTelemetry {@code Meter}.
 *
 * <p>{@link com.cockroachlabs.crdb.metrics.CrdbMetrics} is the facade: calling
 * {@code register()} attaches a
 * {@link com.cockroachlabs.crdb.metrics.CrdbMetricsListener} to the core
 * {@code observe} event bus so retries and circuit trips are counted
 * automatically.
 */
package com.cockroachlabs.crdb.metrics;
