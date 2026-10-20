package com.cockroachlabs.crdb.anomaly;

/**
 * A serialization conflict (SQLSTATE {@code 40001}, or the deadlock variant
 * {@code 40P01}). Retryable: the transaction can be replayed.
 */
public record SerializationConflict(String sqlState, String message, Throwable cause)
        implements CrdbAnomaly {
}
