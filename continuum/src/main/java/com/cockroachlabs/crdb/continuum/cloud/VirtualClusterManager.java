package com.cockroachlabs.crdb.continuum.cloud;

import com.cockroachlabs.crdb.continuum.cloud.CloudClient.VirtualCluster;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Manages Cockroach virtual-cluster lifecycle over a SQL connection using the
 * {@code CREATE / SHOW / DROP VIRTUAL CLUSTER} statements (these require a
 * Continuum-enabled cluster). Names are validated before interpolation into DDL.
 *
 * <p>Works over a small {@link SqlExecutor} so it is testable without a database;
 * {@link #forDataSource} provides a JDBC-backed executor for production.
 */
public final class VirtualClusterManager {

    private static final Pattern IDENT = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]{0,127}$");

    /** The minimal SQL surface the manager needs. */
    public interface SqlExecutor {
        void execute(String sql) throws SQLException;

        List<String[]> query(String sql) throws SQLException;
    }

    private final SqlExecutor executor;

    public VirtualClusterManager(SqlExecutor executor) {
        this.executor = executor;
    }

    /** Creates a manager that runs DDL over a JDBC {@link DataSource}. */
    public static VirtualClusterManager forDataSource(DataSource dataSource) {
        return new VirtualClusterManager(dataSourceExecutor(dataSource));
    }

    private static void validate(String name) {
        if (name == null || !IDENT.matcher(name).matches()) {
            throw new IllegalArgumentException(
                    "cloud: invalid virtual-cluster name '" + name
                            + "' (allowed: letters, digits, underscore; must not start with a digit)");
        }
    }

    public void create(String name) {
        validate(name);
        run("CREATE VIRTUAL CLUSTER " + name);
    }

    public void drop(String name) {
        validate(name);
        run("DROP VIRTUAL CLUSTER " + name);
    }

    private void run(String sql) {
        try {
            executor.execute(sql);
        } catch (SQLException e) {
            throw new RuntimeException("cloud: " + sql + ": " + e.getMessage(), e);
        }
    }

    public List<VirtualCluster> list() {
        try {
            List<String[]> rows = executor.query("SHOW VIRTUAL CLUSTERS");
            List<VirtualCluster> out = new ArrayList<>();
            for (String[] r : rows) {
                String id = r.length > 0 ? r[0] : "";
                String name = r.length > 1 ? r[1] : "";
                out.add(new VirtualCluster(name, id, name, null));
            }
            return out;
        } catch (SQLException e) {
            throw new RuntimeException("cloud: show virtual clusters: " + e.getMessage(), e);
        }
    }

    /**
     * Rewrites a connection string to route to the named virtual cluster via the
     * {@code -ccluster} option. Assumes the base does not already set {@code options}.
     */
    public static String routingConnString(String base, String vclusterName) {
        validate(vclusterName);
        String sep = base.contains("?") ? "&" : "?";
        return base + sep + "options=-ccluster%3D" + vclusterName;
    }

    private static SqlExecutor dataSourceExecutor(DataSource dataSource) {
        return new SqlExecutor() {
            @Override
            public void execute(String sql) throws SQLException {
                try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
                    s.execute(sql);
                }
            }

            @Override
            public List<String[]> query(String sql) throws SQLException {
                try (Connection c = dataSource.getConnection();
                     Statement s = c.createStatement();
                     ResultSet rs = s.executeQuery(sql)) {
                    int cols = rs.getMetaData().getColumnCount();
                    List<String[]> out = new ArrayList<>();
                    while (rs.next()) {
                        String[] row = new String[cols];
                        for (int i = 0; i < cols; i++) {
                            Object o = rs.getObject(i + 1);
                            row[i] = o == null ? "" : o.toString();
                        }
                        out.add(row);
                    }
                    return out;
                }
            }
        };
    }
}
