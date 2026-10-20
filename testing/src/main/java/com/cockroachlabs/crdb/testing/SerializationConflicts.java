package com.cockroachlabs.crdb.testing;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Ready-made workloads that reliably provoke CockroachDB serialization conflicts
 * (SQLSTATE {@code 40001}) for use with {@link ConcurrencyRunner}. Pair the
 * increment task with a {@code SERIALIZABLE} transaction and no retry to observe
 * raw conflict rates, or with the core {@code TransactionTemplate}'s retry
 * enabled to verify conflicts are transparently resolved.
 */
public final class SerializationConflicts {

    private SerializationConflicts() {
    }

    /** Create (if absent) and seed a single-row counter table {@code k TEXT PK, v INT}. */
    public static void createCounter(DataSource ds, String table, String key) {
        try (Connection c = ds.getConnection(); Statement s = c.createStatement()) {
            s.execute("CREATE TABLE IF NOT EXISTS " + table + " (k TEXT PRIMARY KEY, v INT NOT NULL)");
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO " + table + " (k, v) VALUES (?, 0) ON CONFLICT (k) DO NOTHING")) {
                ps.setString(1, key);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException("crdbtest: failed to create counter table", e);
        }
    }

    /** Read the current counter value. */
    public static long readCounter(DataSource ds, String table, String key) {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT v FROM " + table + " WHERE k = ?")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : -1;
            }
        } catch (SQLException e) {
            throw new RuntimeException("crdbtest: failed to read counter", e);
        }
    }

    /**
     * A {@link ConcurrencyRunner.ConcurrentTask} that increments a counter row
     * inside an explicit {@code SERIALIZABLE} transaction via read-then-write,
     * with a brief pause between the read and the write to widen the conflict
     * window. Run concurrently on the same {@code key} to generate {@code 40001}s.
     *
     * <p>This task does <em>not</em> retry: a serialization failure surfaces as a
     * {@link SQLException} so the caller can measure conflicts or wrap the task in
     * the core retry machinery.
     */
    public static ConcurrencyRunner.ConcurrentTask incrementTask(DataSource ds, String table, String key) {
        return (workerId, iteration) -> {
            try (Connection c = ds.getConnection()) {
                c.setAutoCommit(false);
                c.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
                try {
                    long current;
                    try (PreparedStatement sel = c.prepareStatement("SELECT v FROM " + table + " WHERE k = ?")) {
                        sel.setString(1, key);
                        try (ResultSet rs = sel.executeQuery()) {
                            current = rs.next() ? rs.getLong(1) : 0;
                        }
                    }
                    // Widen the window between read and write to maximize contention.
                    Thread.sleep(2);
                    try (PreparedStatement upd = c.prepareStatement("UPDATE " + table + " SET v = ? WHERE k = ?")) {
                        upd.setLong(1, current + 1);
                        upd.setString(2, key);
                        upd.executeUpdate();
                    }
                    c.commit();
                } catch (SQLException | InterruptedException e) {
                    try {
                        c.rollback();
                    } catch (SQLException ignored) {
                        // best effort
                    }
                    throw e;
                }
            }
        };
    }
}
