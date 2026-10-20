package com.cockroachlabs.crdb.anomaly;

/**
 * A connection-level failure (SQLSTATE class {@code 08}), e.g. the node became
 * unreachable or the connection was reset.
 */
public record ConnectionAnomaly(String sqlState, String message, Throwable cause)
        implements CrdbAnomaly {
}
