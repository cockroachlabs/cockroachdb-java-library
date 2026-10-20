package com.cockroachlabs.crdb.tx;

import java.sql.Connection;

/** Transaction isolation levels supported by CockroachDB. */
public enum Isolation {

    /** Strongest isolation; CockroachDB's default. */
    SERIALIZABLE(Connection.TRANSACTION_SERIALIZABLE),

    /** Weaker isolation with fewer retries; available on modern CockroachDB. */
    READ_COMMITTED(Connection.TRANSACTION_READ_COMMITTED);

    private final int jdbcLevel;

    Isolation(int jdbcLevel) {
        this.jdbcLevel = jdbcLevel;
    }

    /** The corresponding {@link java.sql.Connection} isolation constant. */
    public int jdbcLevel() {
        return jdbcLevel;
    }
}
