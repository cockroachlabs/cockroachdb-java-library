package com.cockroachlabs.crdb.observe;

/**
 * A snapshot of process-wide operational counters.
 *
 * @param retryAttempts    total retryable anomalies retried
 * @param retriesExhausted total operations that exhausted their retries
 * @param circuitOpens     total circuit-breaker OPEN transitions
 */
public record CrdbStats(long retryAttempts, long retriesExhausted, long circuitOpens) {
}
