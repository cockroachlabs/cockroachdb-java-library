package com.cockroachlabs.crdb.tx;

import com.cockroachlabs.crdb.annotations.Workload.Priority;
import com.cockroachlabs.crdb.context.CrdbContext;
import com.cockroachlabs.crdb.retry.RetryEngine;
import com.cockroachlabs.crdb.retry.RetryPolicy;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;

/**
 * Runs {@link ConnectionCallback}s inside a managed JDBC transaction.
 *
 * <p>The template opens a connection, sets the requested {@link Isolation},
 * applies follower-read staleness from {@link CrdbContext} (via
 * {@code SET TRANSACTION AS OF SYSTEM TIME}), applies Cockroach vCluster tagging
 * from {@link CrdbContext} — {@code SET LOCAL application_name} for the vCluster
 * name and {@code SET LOCAL default_transaction_quality_of_service} for the
 * workload QoS ({@code HIGH→critical}, {@code NORMAL→regular}, {@code LOW→background}) —
 * runs the callback, and commits. {@code SET LOCAL} scopes the tags to this
 * transaction so pooled connections are not polluted for the next borrower.
 *
 * <p>On a serialization conflict it rolls back and — when {@code retry} is enabled —
 * replays the whole transaction through {@link RetryEngine}, realizing
 * CockroachDB's {@code cockroach_restart} retry semantics as a full-transaction
 * restart.
 *
 * <p><b>vCluster scope (v1):</b> this is workload <em>tagging</em> and QoS
 * assignment. Routing a workload to a different vCluster endpoint is
 * platform-dependent and is not performed here.
 */
public final class TransactionTemplate {

    private final DataSource dataSource;
    private final RetryPolicy retryPolicy;
    private final RetryEngine retryEngine;

    public TransactionTemplate(DataSource dataSource) {
        this(dataSource, RetryPolicy.defaults(), new RetryEngine());
    }

    public TransactionTemplate(DataSource dataSource, RetryPolicy retryPolicy, RetryEngine retryEngine) {
        this.dataSource = dataSource;
        this.retryPolicy = retryPolicy;
        this.retryEngine = retryEngine;
    }

    /**
     * Execute {@code work} in a transaction.
     *
     * @param iso    the isolation level to set
     * @param retry  whether to retry on serialization conflicts
     * @param work   the work to run against the transactional connection
     */
    public <T> T execute(Isolation iso, boolean retry, ConnectionCallback<T> work) {
        if (retry) {
            return retryEngine.run(retryPolicy, () -> doInTransaction(iso, work));
        }
        try {
            return doInTransaction(iso, work);
        } catch (SQLException e) {
            throw new com.cockroachlabs.crdb.anomaly.CrdbException(
                    com.cockroachlabs.crdb.anomaly.AnomalyClassifier.classify(e));
        }
    }

    private <T> T doInTransaction(Isolation iso, ConnectionCallback<T> work) throws SQLException {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            c.setTransactionIsolation(iso.jdbcLevel());

            // AS OF SYSTEM TIME must be the first statement of the transaction.
            Optional<String> staleness = CrdbContext.followerReadStaleness();
            if (staleness.isPresent()) {
                try (Statement s = c.createStatement()) {
                    // Follower read: run against a slightly-stale snapshot served by
                    // the nearest replica.
                    s.execute("SET TRANSACTION AS OF SYSTEM TIME " + staleness.get());
                }
            }

            applyVirtualClusterTags(c);

            try {
                T result = work.run(c);
                c.commit();
                return result;
            } catch (SQLException e) {
                safeRollback(c);
                throw e;
            } catch (RuntimeException e) {
                safeRollback(c);
                throw e;
            }
        }
    }

    /**
     * Apply Cockroach vCluster session tags for this transaction using
     * {@code SET LOCAL} so they revert on commit/rollback (pool-safe).
     */
    private static void applyVirtualClusterTags(Connection c) throws SQLException {
        Optional<String> vcluster = CrdbContext.virtualCluster();
        Optional<Priority> workload = CrdbContext.workload();
        if (vcluster.isEmpty() && workload.isEmpty()) {
            return;
        }
        try (Statement s = c.createStatement()) {
            if (vcluster.isPresent()) {
                s.execute("SET LOCAL application_name = " + quote(vcluster.get()));
            }
            if (workload.isPresent()) {
                s.execute("SET LOCAL default_transaction_quality_of_service = "
                        + quote(workload.get().qos()));
            }
        }
    }

    private static String quote(String value) {
        return "'" + value.replace("'", "''") + "'";
    }

    private static void safeRollback(Connection c) {
        try {
            c.rollback();
        } catch (SQLException ignored) {
            // Best-effort rollback; the original failure is what matters.
        }
    }
}
