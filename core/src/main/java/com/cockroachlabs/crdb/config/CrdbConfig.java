package com.cockroachlabs.crdb.config;

import com.cockroachlabs.crdb.retry.RetryPolicy;
import com.cockroachlabs.crdb.tx.Isolation;

/**
 * Immutable library configuration.
 *
 * @param retry                   retry policy for serialization anomalies
 * @param circuitFailureThreshold consecutive failures before a breaker opens
 * @param circuitResetMillis      how long a breaker stays open before HALF_OPEN
 * @param defaultIsolation        default transaction isolation level
 */
public record CrdbConfig(
        RetryPolicy retry,
        int circuitFailureThreshold,
        long circuitResetMillis,
        Isolation defaultIsolation) {

    /** Sensible defaults for all settings. */
    public static CrdbConfig defaults() {
        return new CrdbConfig(RetryPolicy.defaults(), 5, 30_000L, Isolation.SERIALIZABLE);
    }
}
