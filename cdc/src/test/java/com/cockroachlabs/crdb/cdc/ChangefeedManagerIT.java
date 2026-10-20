package com.cockroachlabs.crdb.cdc;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Live test: the builder-generated {@code CREATE CHANGEFEED} SQL actually creates
 * a changefeed job on a running node (using the {@code null://} sink so no
 * external system is required), and the manager can observe and cancel it.
 * Skipped when no node is reachable.
 */
class ChangefeedManagerIT {

    private static final String URL = System.getenv().getOrDefault(
            "CRDB_URL", "jdbc:postgresql://localhost:26257/parity_java?user=root&sslmode=disable");

    private DataSource ds;
    private String table;

    @BeforeEach
    void setUp() throws SQLException {
        ds = dataSource();
        assumeTrue(reachable(ds), "CockroachDB not reachable at " + URL + " — skipping changefeed IT");
        // Changefeeds require rangefeeds; enabling is idempotent and needs admin (root).
        exec("SET CLUSTER SETTING kv.rangefeed.enabled = true");
        table = "parity_cf_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        exec("CREATE TABLE " + table + " (id INT PRIMARY KEY, v INT)");
        exec("INSERT INTO " + table + " (id, v) VALUES (1, 100)");
    }

    @Test
    void createsObservesAndCancelsChangefeed() {
        ChangefeedManager manager = new ChangefeedManager(ds);
        ChangefeedBuilder builder = ChangefeedBuilder.forTables(table)
                .into("null://")
                .format("json")
                .resolved("5s");

        ChangefeedInfo info = manager.create(builder);
        try {
            assertTrue(info.jobId() > 0, "expected a positive job id, got " + info.jobId());

            String status = manager.status(info.jobId());
            assertNotNull(status, "status should be readable");

            boolean listed = manager.list().stream().anyMatch(ci -> ci.jobId() == info.jobId());
            assertTrue(listed, "created job should appear in SHOW CHANGEFEED JOBS");
        } finally {
            manager.cancel(info.jobId());
        }
    }

    private void exec(String sql) throws SQLException {
        try (Connection c = ds.getConnection(); Statement s = c.createStatement()) {
            s.execute(sql);
        }
    }

    private static DataSource dataSource() {
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setUrl(URL);
        return ds;
    }

    private static boolean reachable(DataSource ds) {
        try (Connection c = ds.getConnection()) {
            return c.isValid(3);
        } catch (SQLException e) {
            return false;
        }
    }
}
