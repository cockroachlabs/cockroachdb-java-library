package com.cockroachlabs.crdb;

import com.cockroachlabs.crdb.annotations.Transactional;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Integration tests against a live CockroachDB node. If the cluster is
 * unreachable, tests are <em>skipped</em> (via assumptions), not failed.
 */
class CrdbIntegrationTest {

    private static final String URL = System.getenv().getOrDefault(
            "CRDB_URL", "jdbc:postgresql://localhost:26257/defaultdb?user=root&sslmode=disable");

    private static DataSource dataSource;
    private static boolean reachable;

    @BeforeAll
    static void connect() {
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setUrl(URL);
        dataSource = ds;
        try (Connection c = ds.getConnection()) {
            reachable = c.isValid(3);
        } catch (SQLException e) {
            reachable = false;
        }
    }

    @BeforeEach
    void requireCluster() {
        Assumptions.assumeTrue(reachable, "CockroachDB not reachable at " + URL + "; skipping");
    }

    // ---- functional core: Crdb.tx ---------------------------------------

    @Test
    void txWritesRow() throws SQLException {
        exec("DROP TABLE IF EXISTS it_tx");
        exec("CREATE TABLE it_tx (id INT PRIMARY KEY, note STRING)");

        Crdb.tx(dataSource, c -> {
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO it_tx (id, note) VALUES (?, ?)")) {
                ps.setInt(1, 1);
                ps.setString(2, "hello");
                ps.executeUpdate();
            }
            return null;
        });

        assertEquals(1, count("SELECT count(*) FROM it_tx"));
    }

    // ---- annotation-driven: @Transactional through Crdb.enhance ----------

    interface AccountService {
        void createAccount(int id, int balance);
    }

    static final class AccountServiceImpl implements AccountService {
        private final DataSource ds;

        AccountServiceImpl(DataSource ds) {
            this.ds = ds;
        }

        @Override
        @Transactional(retry = true)
        public void createAccount(int id, int balance) {
            Crdb.tx(ds, c -> {
                try (PreparedStatement ps =
                             c.prepareStatement("INSERT INTO it_accounts (id, balance) VALUES (?, ?)")) {
                    ps.setInt(1, id);
                    ps.setInt(2, balance);
                    ps.executeUpdate();
                }
                return null;
            });
        }
    }

    @Test
    void enhancedTransactionalMethodWritesRow() throws SQLException {
        exec("DROP TABLE IF EXISTS it_accounts");
        exec("CREATE TABLE it_accounts (id INT PRIMARY KEY, balance INT)");

        AccountService svc = Crdb.enhance((AccountService) new AccountServiceImpl(dataSource));
        svc.createAccount(7, 500);

        assertEquals(1, count("SELECT count(*) FROM it_accounts WHERE id = 7"));
        assertEquals(500, count("SELECT balance FROM it_accounts WHERE id = 7"));
    }

    // ---- forced serialization conflict, resolved by retry ----------------

    @Test
    void concurrentConflictEventuallySucceeds() throws Exception {
        exec("DROP TABLE IF EXISTS it_accounts");
        exec("CREATE TABLE it_accounts (id INT PRIMARY KEY, balance INT)");
        exec("INSERT INTO it_accounts (id, balance) VALUES (1, 100)");

        Runnable addTen = () -> Crdb.tx(dataSource, c -> {
            int balance;
            try (PreparedStatement ps = c.prepareStatement("SELECT balance FROM it_accounts WHERE id = 1")) {
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    balance = rs.getInt(1);
                }
            }
            sleepQuietly(); // widen the conflict window
            try (PreparedStatement up = c.prepareStatement("UPDATE it_accounts SET balance = ? WHERE id = 1")) {
                up.setInt(1, balance + 10);
                up.executeUpdate();
            }
            return null;
        });

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> f1 = pool.submit(addTen);
            Future<?> f2 = pool.submit(addTen);
            f1.get(30, TimeUnit.SECONDS);
            f2.get(30, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }

        assertEquals(120, count("SELECT balance FROM it_accounts WHERE id = 1"),
                "both read-modify-writes committed thanks to retry");
    }

    // ---- helpers ---------------------------------------------------------

    private static void exec(String sql) throws SQLException {
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.execute(sql);
        }
    }

    private static int count(String sql) throws SQLException {
        try (Connection c = dataSource.getConnection();
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private static void sleepQuietly() {
        try {
            Thread.sleep(80);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
