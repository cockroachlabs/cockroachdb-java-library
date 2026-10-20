package com.cockroachlabs.crdb.anomaly;

/**
 * Any failure that does not map to a more specific {@link CrdbAnomaly}. Treated
 * as non-retryable by default.
 */
public record UnknownAnomaly(String sqlState, String message, Throwable cause)
        implements CrdbAnomaly {
}
