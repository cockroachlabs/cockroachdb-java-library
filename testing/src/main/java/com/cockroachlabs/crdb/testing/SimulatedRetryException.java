package com.cockroachlabs.crdb.testing;

import java.sql.SQLException;

/**
 * A {@link SQLException} carrying SQLSTATE {@code 40001} (serialization failure),
 * mimicking CockroachDB's retryable transaction-conflict error. Because the core
 * library's anomaly classifier treats {@code 40001} as retryable, throwing this
 * from a unit of work exercises the real retry path without a live database.
 */
public final class SimulatedRetryException extends SQLException {

    private static final long serialVersionUID = 1L;

    /** SQLSTATE for a serialization failure. */
    public static final String SERIALIZATION_FAILURE = "40001";

    private final int attempt;

    public SimulatedRetryException(int attempt) {
        super("crdbtest: simulated serialization failure (attempt " + attempt + ", SQLSTATE 40001)",
                SERIALIZATION_FAILURE);
        this.attempt = attempt;
    }

    /** The (0-based) attempt index at which this simulated failure was produced. */
    public int attempt() {
        return attempt;
    }
}
