package com.cockroachlabs.crdb.testing;

import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.CockroachContainer;
import org.testcontainers.utility.DockerImageName;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * A CockroachDB test harness with two backends:
 *
 * <ul>
 *   <li><b>container</b> — spins up an ephemeral single-node CockroachDB via
 *       Testcontainers ({@link #container()}). Requires Docker <em>at test
 *       runtime</em> only; this class compiles and links without a running
 *       Docker daemon.</li>
 *   <li><b>external</b> — targets an already-running node by JDBC URL
 *       ({@link #external(String)}), e.g. a local dev cluster or a CI service.</li>
 * </ul>
 *
 * <p>{@link AutoCloseable} so it can be used in try-with-resources; closing a
 * container-backed harness stops the container, while closing an external one is
 * a no-op.
 */
public final class CockroachHarness implements AutoCloseable {

    /** A reasonable default image tag for the container backend. */
    public static final String DEFAULT_IMAGE = "cockroachdb/cockroach:v24.1.5";

    private final CockroachContainer container; // null in external mode
    private String jdbcUrl;                      // set in external mode, or after start()

    private CockroachHarness(CockroachContainer container, String jdbcUrl) {
        this.container = container;
        this.jdbcUrl = jdbcUrl;
    }

    /** Container backend using {@link #DEFAULT_IMAGE}. Call {@link #start()} before use. */
    public static CockroachHarness container() {
        return container(DEFAULT_IMAGE);
    }

    /** Container backend using the given image. Call {@link #start()} before use. */
    public static CockroachHarness container(String image) {
        return new CockroachHarness(
                new CockroachContainer(DockerImageName.parse(image)), null);
    }

    /** External backend targeting an already-running node at {@code jdbcUrl}. */
    public static CockroachHarness external(String jdbcUrl) {
        return new CockroachHarness(null, jdbcUrl);
    }

    /**
     * Start the container backend (no-op for the external backend). Requires
     * Docker to be available.
     */
    public CockroachHarness start() {
        if (container != null && jdbcUrl == null) {
            container.start();
            this.jdbcUrl = container.getJdbcUrl();
        }
        return this;
    }

    /** The effective JDBC URL. For a container backend, valid only after {@link #start()}. */
    public String jdbcUrl() {
        if (jdbcUrl == null) {
            throw new IllegalStateException("harness not started; call start() first");
        }
        return jdbcUrl;
    }

    /** A {@link DataSource} for the effective JDBC URL. */
    public DataSource dataSource() {
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setUrl(jdbcUrl());
        return ds;
    }

    /** Whether the node is reachable within a short timeout. */
    public boolean reachable() {
        try (Connection c = dataSource().getConnection()) {
            return c.isValid(3);
        } catch (SQLException e) {
            return false;
        }
    }

    /** Execute DDL/seed statements sequentially against the node. */
    public void applySchema(String... statements) {
        try (Connection c = dataSource().getConnection(); Statement s = c.createStatement()) {
            for (String stmt : statements) {
                s.execute(stmt);
            }
        } catch (SQLException e) {
            throw new RuntimeException("crdbtest: schema application failed", e);
        }
    }

    @Override
    public void close() {
        if (container != null) {
            container.stop();
        }
    }
}
