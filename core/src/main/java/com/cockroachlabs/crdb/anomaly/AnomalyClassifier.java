package com.cockroachlabs.crdb.anomaly;

import java.sql.SQLException;

/**
 * Maps raw {@link SQLException}s onto the typed {@link CrdbAnomaly} hierarchy and
 * decides retryability, based on SQLSTATE.
 *
 * <ul>
 *   <li>{@code 40001} (serialization_failure) and {@code 40P01} (deadlock) →
 *       {@link SerializationConflict} — retryable.</li>
 *   <li>class {@code 08*} (connection exceptions) → {@link ConnectionAnomaly} —
 *       retryable (a fresh connection may succeed).</li>
 *   <li>everything else → {@link UnknownAnomaly} — not retryable.</li>
 * </ul>
 */
public final class AnomalyClassifier {

    /** Serialization failure. */
    public static final String SQLSTATE_SERIALIZATION_FAILURE = "40001";
    /** Deadlock detected (also treated as a serialization conflict on CockroachDB). */
    public static final String SQLSTATE_DEADLOCK = "40P01";

    private AnomalyClassifier() {
    }

    /** Classify a {@link SQLException} into a typed {@link CrdbAnomaly}. */
    public static CrdbAnomaly classify(SQLException e) {
        String state = e.getSQLState();
        String msg = e.getMessage();
        if (state == null) {
            return new UnknownAnomaly(null, msg, e);
        }
        if (SQLSTATE_SERIALIZATION_FAILURE.equals(state) || SQLSTATE_DEADLOCK.equals(state)) {
            return new SerializationConflict(state, msg, e);
        }
        if (state.startsWith("08")) {
            return new ConnectionAnomaly(state, msg, e);
        }
        return new UnknownAnomaly(state, msg, e);
    }

    /**
     * Whether a {@link SQLException} represents a retryable anomaly. Retryable
     * kinds are serialization conflicts and connection anomalies.
     */
    public static boolean isRetryable(SQLException e) {
        return switch (classify(e)) {
            case SerializationConflict ignored -> true;
            case ConnectionAnomaly ignored -> true;
            case RetryExhausted ignored -> false;
            case CircuitOpen ignored -> false;
            case UnknownAnomaly ignored -> false;
        };
    }
}
