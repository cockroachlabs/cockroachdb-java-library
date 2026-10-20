package com.cockroachlabs.crdb.retry;

import com.cockroachlabs.crdb.anomaly.AnomalyClassifier;
import com.cockroachlabs.crdb.anomaly.CrdbAnomaly;
import com.cockroachlabs.crdb.anomaly.CrdbException;
import com.cockroachlabs.crdb.anomaly.RetryExhausted;

import java.sql.SQLException;
import java.util.concurrent.Callable;
import java.util.function.DoubleSupplier;

/**
 * Executes a unit of work, retrying on retryable CockroachDB anomalies with
 * {@link AdaptiveBackoff}. When attempts are exhausted it throws
 * {@link CrdbException} carrying a {@link RetryExhausted} anomaly.
 *
 * <p>The engine keeps a persistent {@link AdaptiveBackoff} so contention learned
 * from one operation informs the next.
 */
public final class RetryEngine {

    /** Abstracts sleeping so tests can run without real delays. */
    @FunctionalInterface
    public interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }

    private final DoubleSupplier jitterSource;
    private final Sleeper sleeper;

    private AdaptiveBackoff backoff;
    private RetryPolicy backoffPolicy;

    public RetryEngine() {
        this(null, Thread::sleep);
    }

    /**
     * @param jitterSource optional deterministic jitter source (may be {@code null})
     * @param sleeper      how to wait between attempts (inject a no-op in tests)
     */
    public RetryEngine(DoubleSupplier jitterSource, Sleeper sleeper) {
        this.jitterSource = jitterSource;
        this.sleeper = sleeper;
    }

    /**
     * Run {@code work}, retrying on retryable anomalies per {@code policy}.
     *
     * @throws CrdbException with a {@link RetryExhausted} anomaly when all
     *                       attempts fail, or with the classified anomaly for a
     *                       non-retryable {@link SQLException}
     */
    public <T> T run(RetryPolicy policy, Callable<T> work) {
        AdaptiveBackoff bo = backoffFor(policy);
        CrdbAnomaly last = null;
        int attempt = 0;
        while (true) {
            try {
                T result = work.call();
                bo.record(attempt); // attempt == retries actually used
                return result;
            } catch (Exception ex) {
                SQLException sql = findSqlException(ex);
                if (sql == null) {
                    // Not a database anomaly: propagate faithfully.
                    throw asRuntime(ex);
                }
                if (!AnomalyClassifier.isRetryable(sql)) {
                    throw new CrdbException(AnomalyClassifier.classify(sql));
                }
                last = AnomalyClassifier.classify(sql);
                String state = sql.getSQLState();
                com.cockroachlabs.crdb.observe.CrdbEvents.fireRetryAttempt("", attempt + 1, state);
                if (attempt >= policy.maxRetries()) {
                    bo.record(attempt);
                    com.cockroachlabs.crdb.observe.CrdbEvents.fireRetryExhausted("", attempt + 1, state);
                    throw new CrdbException(new RetryExhausted(attempt + 1, last));
                }
                sleepQuietly(bo.nextDelayMillis(attempt));
                attempt++;
            }
        }
    }

    private synchronized AdaptiveBackoff backoffFor(RetryPolicy policy) {
        if (backoff == null || !policy.equals(backoffPolicy)) {
            backoff = jitterSource == null
                    ? new AdaptiveBackoff(policy)
                    : new AdaptiveBackoff(policy, jitterSource);
            backoffPolicy = policy;
        }
        return backoff;
    }

    private void sleepQuietly(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            sleeper.sleep(millis);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new CrdbException(new RetryExhausted(0,
                    com.cockroachlabs.crdb.anomaly.AnomalyClassifier.classify(
                            new SQLException("interrupted during backoff", "08006", ie))));
        }
    }

    private static SQLException findSqlException(Throwable t) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (c instanceof SQLException sql) {
                return sql;
            }
            if (c.getCause() == c) {
                break;
            }
        }
        return null;
    }

    private static RuntimeException asRuntime(Exception ex) {
        if (ex instanceof RuntimeException re) {
            return re;
        }
        return new RuntimeException(ex);
    }
}
