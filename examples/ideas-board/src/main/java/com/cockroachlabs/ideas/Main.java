package com.cockroachlabs.ideas;

import com.cockroachlabs.crdb.testing.ConcurrencyResult;
import com.cockroachlabs.crdb.testing.ConcurrencyRunner;
import org.postgresql.ds.PGSimpleDataSource;

import javax.sql.DataSource;

// tag::runner[]
/**
 * Seeds one idea, then hammers it with concurrent upvotes two ways using the
 * library's {@link ConcurrencyRunner}:
 *
 * <ol>
 *   <li>{@code upvoteWithoutRetry} — plain SERIALIZABLE; conflicts surface as
 *       40001 failures and votes are lost.</li>
 *   <li>{@code upvote} — {@code Crdb.tx} auto-retries; every vote lands.</li>
 * </ol>
 *
 * <p>Point it at a running cluster via the {@code JDBC_URL} env var, or start a
 * local one: {@code cockroach start-single-node --insecure}.
 */
public final class Main {

    private static final int WORKERS = 3;
    private static final int ITERATIONS = 20;
    private static final int EXPECTED = WORKERS * ITERATIONS; // 60

    public static void main(String[] args) {
        DataSource ds = dataSource();
        IdeaRepository repo = new IdeaRepository(ds);
        IdeaService service = new IdeaService(ds, repo);

        repo.createSchema();
        Idea idea = repo.seed("Add dark mode to the ideas board");
        System.out.println("Seeded idea " + idea.id() + " — expecting " + EXPECTED + " upvotes\n");

        ConcurrencyRunner runner = new ConcurrencyRunner(WORKERS, ITERATIONS);

        // 1) No retry: conflicts are lost.
        repo.reset(idea.id());
        ConcurrencyResult plain = runner.run((w, i) -> service.upvoteWithoutRetry(idea.id()));
        report("WITHOUT retry (plain)", plain, repo.currentVotes(idea.id()));

        // 2) With Crdb.tx retry: every vote counted.
        repo.reset(idea.id());
        ConcurrencyResult retry = runner.run((w, i) -> service.upvote(idea.id()));
        report("WITH Crdb.tx retry", retry, repo.currentVotes(idea.id()));
    }

    private static void report(String label, ConcurrencyResult r, int votes) {
        System.out.printf(
                "%-24s successes=%d failures=%d votes=%d/%d%n",
                label, r.successes(), r.failures(), votes, EXPECTED);
    }

    private static DataSource dataSource() {
        String url = System.getenv().getOrDefault(
                "JDBC_URL",
                "jdbc:postgresql://localhost:26257/defaultdb?sslmode=disable&user=root");
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setUrl(url);
        return ds;
    }
}
// end::runner[]
