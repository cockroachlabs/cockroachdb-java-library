package com.cockroachlabs.ideas;

import com.cockroachlabs.crdb.testing.CockroachHarness;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Proves the follower-read leaderboard end to end against a real CockroachDB node:
 * the {@code @FollowerRead}-annotated {@link IdeaReadService} reads at
 * {@code follower_read_timestamp()} and returns the ideas ordered by votes.
 *
 * <p>Because a follower read is historical (~4.8s stale), freshly-seeded rows are
 * invisible until wall-clock time passes the staleness window, so the test polls
 * until the snapshot catches up rather than asserting immediately.
 *
 * <p>Requires Docker at test runtime.
 */
class IdeaFollowerReadIT {

    // tag::follower-read-it[]
    @Test
    void topIdeasReturnsVotesOrderedFromAFollowerSnapshot() {
        try (CockroachHarness harness = CockroachHarness.container().start()) {
            // The harness URL has no user; the Testcontainers node authenticates as root.
            PGSimpleDataSource ds = new PGSimpleDataSource();
            ds.setUrl(harness.jdbcUrl());
            ds.setUser("root");

            IdeaRepository repo = new IdeaRepository(ds);
            repo.createSchema();
            insert(ds, "Dark mode", 5);
            insert(ds, "Full-text search", 12);
            insert(ds, "CSV export", 3);

            IdeaReadService reads = IdeaReadService.create(ds);

            // Poll: the seeded rows appear once follower_read_timestamp() advances
            // past their insert time.
            List<Idea> top = awaitSize(() -> reads.topIdeas(10), 3, Duration.ofSeconds(30));

            assertFalse(top.isEmpty(), "follower read must return the seeded ideas");
            assertEquals(3, top.size());
            assertEquals("Full-text search", top.get(0).text(), "highest-voted idea must sort first");
            assertEquals(12, top.get(0).votes());
            for (int i = 1; i < top.size(); i++) {
                assertTrue(top.get(i - 1).votes() >= top.get(i).votes(),
                        "leaderboard must be ordered by votes descending");
            }
        }
    }
    // end::follower-read-it[]

    /** Insert one idea with an explicit vote count (seed() always starts at zero). */
    private static void insert(DataSource ds, String text, int votes) {
        try (Connection c = ds.getConnection();
             PreparedStatement ps =
                     c.prepareStatement("INSERT INTO idea (id, text, votes) VALUES (?, ?, ?)")) {
            ps.setObject(1, UUID.randomUUID());
            ps.setString(2, text);
            ps.setInt(3, votes);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException("insert failed", e);
        }
    }

    /**
     * Call {@code read} until it returns {@code expected} rows or the deadline
     * passes. A follower read runs {@code AS OF SYSTEM TIME follower_read_timestamp()}
     * (~4.8s in the past); on a seconds-old container that timestamp can predate the
     * freshly-created schema, so the read transiently errors with "does not exist"
     * until the staleness window advances. Those are expected while the window
     * catches up, so we poll through them.
     */
    private static List<Idea> awaitSize(java.util.function.Supplier<List<Idea>> read,
                                        int expected, Duration timeout) {
        Instant deadline = Instant.now().plus(timeout);
        List<Idea> last = List.of();
        RuntimeException lastError = null;
        while (Instant.now().isBefore(deadline)) {
            try {
                last = read.get();
                lastError = null;
                if (last.size() >= expected) {
                    return last;
                }
            } catch (RuntimeException e) {
                lastError = e; // as-of-time read before schema existed; retry as window advances
            }
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        }
        if (lastError != null) {
            throw new AssertionError("follower read never succeeded before deadline", lastError);
        }
        fail("follower read never caught up: expected " + expected + " rows, last saw " + last.size());
        return last; // unreachable
    }
}
