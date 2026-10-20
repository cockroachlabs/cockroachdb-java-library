package com.cockroachlabs.crdb.anomaly;

/**
 * A typed CockroachDB failure. Sealed so that every kind can be handled with an
 * exhaustive pattern-matching {@code switch} (no {@code default} needed).
 */
public sealed interface CrdbAnomaly
        permits SerializationConflict, ConnectionAnomaly, RetryExhausted, CircuitOpen, UnknownAnomaly {

    /** The SQLSTATE associated with this anomaly, or {@code null} if not applicable. */
    String sqlState();

    /** A human-readable description of the anomaly. */
    String message();
}
