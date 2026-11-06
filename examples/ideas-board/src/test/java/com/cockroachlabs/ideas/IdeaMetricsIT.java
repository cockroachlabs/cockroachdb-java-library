package com.cockroachlabs.ideas;

import com.cockroachlabs.crdb.metrics.CrdbMetrics;
import com.cockroachlabs.crdb.metrics.InMemoryMetricsRecorder;
import com.cockroachlabs.crdb.metrics.MetricsSnapshot;
import com.cockroachlabs.crdb.testing.ConcurrencyResult;
import com.cockroachlabs.crdb.testing.ConcurrencyRunner;
import com.cockroachlabs.crdb.testing.CockroachHarness;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves the metrics module counts real work: under contention the retrying
 * {@code upvote} fires retry events on the core observe bus, and wrapping each
 * upvote in {@link CrdbMetrics#timeTransaction} records a transaction. The
 * in-memory recorder's snapshot must show both counters moved.
 *
 * <p>Requires Docker at test runtime.
 */
class IdeaMetricsIT {

    private static final int WORKERS = 4;
    private static final int ITERATIONS = 25;
    private static final int EXPECTED = WORKERS * ITERATIONS; // 100

    @Test
    void transactionAndRetryCountersMoveUnderContention() {
        try (CockroachHarness harness = CockroachHarness.container().start()) {
            PGSimpleDataSource ds = new PGSimpleDataSource();
            ds.setUrl(harness.jdbcUrl());
            ds.setUser("root");

            IdeaRepository repo = new IdeaRepository(ds);
            IdeaService service = new IdeaService(ds, repo);
            repo.createSchema();
            Idea idea = repo.seed("Instrument the ideas board");

            // tag::metrics-config[]
            // Enable metrics: attach a recorder to the core observe bus so every
            // retry attempt the library makes is counted automatically.
            CrdbMetrics metrics = CrdbMetrics.inMemory().register();
            // end::metrics-config[]
            try {
                // Each upvote is timed as one transaction; the inner Crdb.tx retries
                // on 40001 are counted independently via the observe bus.
                ConcurrencyRunner runner = new ConcurrencyRunner(WORKERS, ITERATIONS);
                ConcurrencyResult result = runner.run((w, i) ->
                        metrics.timeTransaction(() -> {
                            service.upvote(idea.id());
                            return null;
                        }));

                assertEquals(0, result.failures(), "retry must absorb every conflict");
                assertEquals(EXPECTED, repo.currentVotes(idea.id()), "every upvote must land");

                MetricsSnapshot snap = ((InMemoryMetricsRecorder) metrics.recorder()).snapshot();
                assertEquals(EXPECTED, snap.transactionCount(),
                        "one recorded transaction per timed upvote");
                assertEquals(EXPECTED, snap.transactionSuccessCount());
                assertTrue(snap.retryAttempts() > 0,
                        "contention on one row must force at least one 40001 retry, but retryAttempts="
                                + snap.retryAttempts());
            } finally {
                metrics.unregister();
            }
        }
    }
}
