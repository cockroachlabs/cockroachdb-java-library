package com.cockroachlabs.crdb.cdc;

import com.cockroachlabs.crdb.anomaly.AnomalyClassifier;
import com.cockroachlabs.crdb.anomaly.CrdbException;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Creates and controls CockroachDB changefeed jobs over a {@link DataSource}.
 *
 * <p>{@link #create(ChangefeedBuilder)} runs the builder's {@code CREATE CHANGEFEED}
 * statement and returns the resulting job id; {@link #pause(long)},
 * {@link #resume(long)}, and {@link #cancel(long)} drive the job lifecycle via
 * {@code PAUSE/RESUME/CANCEL JOB}. SQL failures are surfaced as
 * {@link CrdbException} so callers share one error model with the core library.
 */
public final class ChangefeedManager {

    private final DataSource dataSource;

    public ChangefeedManager(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    /** Create a changefeed from a builder and return its job metadata. */
    public ChangefeedInfo create(ChangefeedBuilder builder) {
        return create(builder.toSql());
    }

    /** Create a changefeed from raw SQL and return its job metadata. */
    public ChangefeedInfo create(String createChangefeedSql) {
        try (Connection c = dataSource.getConnection();
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery(createChangefeedSql)) {
            long jobId = 0;
            if (rs.next()) {
                jobId = rs.getLong(1);
            }
            return new ChangefeedInfo(jobId, "running", Instant.now());
        } catch (SQLException e) {
            throw new CrdbException(AnomalyClassifier.classify(e));
        }
    }

    /** Pause a running changefeed job. */
    public void pause(long jobId) {
        jobControl("PAUSE JOB " + jobId);
    }

    /** Resume a paused changefeed job. */
    public void resume(long jobId) {
        jobControl("RESUME JOB " + jobId);
    }

    /** Cancel a changefeed job. */
    public void cancel(long jobId) {
        jobControl("CANCEL JOB " + jobId);
    }

    /** The current status of a changefeed job (e.g. {@code running}, {@code paused}), or {@code null} if unknown. */
    public String status(long jobId) {
        try (Connection c = dataSource.getConnection();
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SHOW CHANGEFEED JOB " + jobId)) {
            if (rs.next()) {
                return rs.getString("status");
            }
            return null;
        } catch (SQLException e) {
            throw new CrdbException(AnomalyClassifier.classify(e));
        }
    }

    /** List currently known changefeed jobs and their statuses. */
    public List<ChangefeedInfo> list() {
        List<ChangefeedInfo> out = new ArrayList<>();
        try (Connection c = dataSource.getConnection();
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SHOW CHANGEFEED JOBS")) {
            while (rs.next()) {
                out.add(new ChangefeedInfo(rs.getLong("job_id"), rs.getString("status"), Instant.now()));
            }
            return out;
        } catch (SQLException e) {
            throw new CrdbException(AnomalyClassifier.classify(e));
        }
    }

    private void jobControl(String sql) {
        try (Connection c = dataSource.getConnection();
             Statement s = c.createStatement()) {
            s.execute(sql);
        } catch (SQLException e) {
            throw new CrdbException(AnomalyClassifier.classify(e));
        }
    }
}
