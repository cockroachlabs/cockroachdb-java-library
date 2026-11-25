package com.cockroachlabs.crdb.anomaly;

import org.jspecify.annotations.Nullable;

/**
 * Any failure that does not map to a more specific {@link CrdbAnomaly}. Treated
 * as non-retryable by default.
 */
public record UnknownAnomaly(@Nullable String sqlState, String message, Throwable cause)
        implements CrdbAnomaly {
}
