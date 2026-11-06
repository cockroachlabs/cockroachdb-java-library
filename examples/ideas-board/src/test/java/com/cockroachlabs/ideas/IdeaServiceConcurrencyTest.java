package com.cockroachlabs.ideas;

import com.cockroachlabs.crdb.testing.CockroachHarness;
import com.cockroachlabs.crdb.testing.ConcurrencyResult;
import com.cockroachlabs.crdb.testing.ConcurrencyRunner;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves the hero feature under real contention: a Testcontainers CockroachDB
 * node, three worker threads, twenty upvotes each (60 total) racing on one row.
 *
 * <p>Requires Docker at test runtime.
 */
class IdeaServiceConcurrencyTest {

    private static final int WORKERS = 3;
    private static final int ITERATIONS = 20;
    private static final int EXPECTED = WORKERS * ITERATIONS; // 60

    @Test
    void retryLandsEveryVoteWhilePlainLosesSome() {
        try (CockroachHarness harness = CockroachHarness.container().start()) {
            // CockroachHarness.dataSource() sets only the URL; the Testcontainers
            // node authenticates as user "root", so build the DataSource from the
            // harness URL with that user set explicitly.
            PGSimpleDataSource ds = new PGSimpleDataSource();
            ds.setUrl(harness.jdbcUrl());
            ds.setUser("root");

            IdeaRepository repo = new IdeaRepository(ds);
            IdeaService service = new IdeaService(ds, repo);

            repo.createSchema();
            Idea idea = repo.seed("Concurrent upvotes");
            ConcurrencyRunner runner = new ConcurrencyRunner(WORKERS, ITERATIONS);

            // tag::concurrency-test[]
            // Plain path: SERIALIZABLE conflicts raise 40001 and are lost, so the
            // final count does NOT reliably reach 60.
            repo.reset(idea.id());
            ConcurrencyResult plain = runner.run((w, i) -> service.upvoteWithoutRetry(idea.id()));
            int plainVotes = repo.currentVotes(idea.id());
            assertTrue(plainVotes < EXPECTED || plain.failures() > 0,
                    "plain upvote should lose votes under contention, but got votes="
                            + plainVotes + " with failures=" + plain.failures());

            // Retry path: Crdb.tx replays the transaction on every 40001, so all
            // 60 votes land with zero failures.
            repo.reset(idea.id());
            ConcurrencyResult retry = runner.run((w, i) -> service.upvote(idea.id()));
            assertEquals(0, retry.failures(),
                    "Crdb.tx retry must absorb all serialization conflicts");
            assertEquals(EXPECTED, repo.currentVotes(idea.id()),
                    "every upvote must be counted with retry");
            // end::concurrency-test[]
        }
    }
}
