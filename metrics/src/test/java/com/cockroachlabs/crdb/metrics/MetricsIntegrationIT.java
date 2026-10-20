package com.cockroachlabs.crdb.metrics;

import com.cockroachlabs.crdb.observe.CrdbEvents;
import com.cockroachlabs.crdb.testing.ConcurrencyResult;
import com.cockroachlabs.crdb.testing.ConcurrencyRunner;
import com.cockroachlabs.crdb.tx.ConnectionCallback;
import com.cockroachlabs.crdb.tx.Isolation;
import com.cockroachlabs.crdb.tx.TransactionTemplate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Live test: drives real serialization conflicts through the core
 * {@link TransactionTemplate} and asserts the metrics recorder counted the
 * retries automatically via the observe bus. Skipped (via assumption) when no
 * CockroachDB node is reachable, so {@code ./gradlew build} stays green in CI.
 */
class MetricsIntegrationIT {

    private static final String URL = System.getenv().getOrDefault(
            "CRDB_URL", "jdbc:postgresql://localhost:26257/parity_java?user=root&sslmode=disable");
    private static final String TABLE = "parity_metrics_counter";
    private static final String KEY = "k";

    private DataSource ds;

    @BeforeEach
    void setUp() throws SQLException {
        ds = dataSource();
        assumeTrue(reachable(ds), "CockroachDB not reachable at " + URL + " — skipping live metrics IT");
        exec("CREATE TABLE IF NOT EXISTS " + TABLE + " (k TEXT PRIMARY KEY, v INT NOT NULL)");
        exec("UPSERT INTO " + TABLE + " (k, v) VALUES ('" + KEY + "', 0)");
        CrdbEvents.clearListeners();
    }

    @AfterEach
    void tearDown() {
        CrdbEvents.clearListeners();
    }

    @Test
    void retriesAreCountedAfterRealConflicts() throws SQLException {
        InMemoryMetricsRecorder recorder = new InMemoryMetricsRecorder();
        CrdbMetrics metrics = new CrdbMetrics(recorder).register();
        try {
            TransactionTemplate template = new TransactionTemplate(ds);
            int workers = 6;
            int iterations = 20;

            ConnectionCallback<Void> increment = conn -> {
                long v;
                try (PreparedStatement sel = conn.prepareStatement("SELECT v FROM " + TABLE + " WHERE k = ?")) {
                    sel.setString(1, KEY);
                    try (ResultSet rs = sel.executeQuery()) {
                        rs.next();
                        v = rs.getLong(1);
                    }
                }
                try (PreparedStatement upd = conn.prepareStatement("UPDATE " + TABLE + " SET v = ? WHERE k = ?")) {
                    upd.setLong(1, v + 1);
                    upd.setString(2, KEY);
                    upd.executeUpdate();
                }
                return null;
            };

            ConcurrencyResult result = new ConcurrencyRunner(workers, iterations).run(
                    (w, i) -> template.execute(Isolation.SERIALIZABLE, true, increment));

            // With retry enabled, every increment must eventually commit.
            assertEquals(workers * iterations, result.successes(), "all increments should succeed with retry");
            assertEquals(workers * iterations, counter(), "final counter must equal number of increments");

            // Contention on a single row must have produced at least one retry,
            // and the metrics recorder must have counted it via the observe bus.
            assertTrue(recorder.snapshot().retryAttempts() > 0,
                    "expected retries to be recorded; got " + recorder.snapshot());
        } finally {
            metrics.unregister();
        }
    }

    private long counter() throws SQLException {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT v FROM " + TABLE + " WHERE k = ?")) {
            ps.setString(1, KEY);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
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
