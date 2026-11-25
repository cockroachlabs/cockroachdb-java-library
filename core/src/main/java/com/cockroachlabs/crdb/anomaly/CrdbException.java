package com.cockroachlabs.crdb.anomaly;

import org.jspecify.annotations.Nullable;

/**
 * Unchecked, typed failure that wraps a {@link CrdbAnomaly}. Callers get a
 * single exception type with an {@link #anomaly()} accessor and can branch
 * exhaustively over the sealed hierarchy.
 */
public final class CrdbException extends RuntimeException {

    private final transient CrdbAnomaly anomaly;

    public CrdbException(CrdbAnomaly anomaly) {
        super(anomaly.message(), causeOf(anomaly));
        this.anomaly = anomaly;
    }

    /** The typed anomaly this exception carries. */
    public CrdbAnomaly anomaly() {
        return anomaly;
    }

    /**
     * Formats an anomaly for logging/telemetry using an exhaustive
     * pattern-matching {@code switch} over the sealed hierarchy — the compiler
     * guarantees every kind is handled.
     */
    public static String describe(CrdbAnomaly anomaly) {
        return switch (anomaly) {
            case SerializationConflict sc ->
                    "SERIALIZATION_CONFLICT[" + sc.sqlState() + "]: " + sc.message();
            case ConnectionAnomaly ca ->
                    "CONNECTION[" + ca.sqlState() + "]: " + ca.message();
            case RetryExhausted re ->
                    "RETRY_EXHAUSTED after " + re.attempts() + " attempt(s) -> " + describe(re.last());
            case CircuitOpen co ->
                    "CIRCUIT_OPEN: " + co.message();
            case UnknownAnomaly ua ->
                    "UNKNOWN[" + ua.sqlState() + "]: " + ua.message();
        };
    }

    private static @Nullable Throwable causeOf(CrdbAnomaly anomaly) {
        return switch (anomaly) {
            case SerializationConflict sc -> sc.cause();
            case ConnectionAnomaly ca -> ca.cause();
            case UnknownAnomaly ua -> ua.cause();
            case RetryExhausted re -> causeOf(re.last());
            case CircuitOpen ignored -> null;
        };
    }
}
