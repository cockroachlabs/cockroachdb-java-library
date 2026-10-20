package com.cockroachlabs.crdb.continuum;

import org.postgresql.ds.PGSimpleDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/** Shared test helpers for continuum integration tests. */
final class TestSupport {

    static final String URL = System.getenv().getOrDefault(
            "CRDB_URL", "jdbc:postgresql://localhost:26257/defaultdb?user=root&sslmode=disable");

    private TestSupport() {
    }

    static DataSource dataSource() {
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setUrl(URL);
        return ds;
    }

    static boolean reachable(DataSource ds) {
        try (Connection c = ds.getConnection()) {
            return c.isValid(3);
        } catch (SQLException e) {
            return false;
        }
    }

    static void exec(DataSource ds, String sql) throws SQLException {
        try (Connection c = ds.getConnection(); Statement s = c.createStatement()) {
            s.execute(sql);
        }
    }
}
