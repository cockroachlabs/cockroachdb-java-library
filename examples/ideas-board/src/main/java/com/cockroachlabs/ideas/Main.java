package com.cockroachlabs.ideas;

import com.cockroachlabs.crdb.Crdb;
import com.cockroachlabs.crdb.cdc.CdcEvent;
import com.cockroachlabs.crdb.cdc.ChangefeedHandlerRegistry;
import com.cockroachlabs.crdb.cdc.Operation;
import com.cockroachlabs.crdb.metrics.CrdbMetrics;
import com.cockroachlabs.crdb.metrics.InMemoryMetricsRecorder;
import com.cockroachlabs.crdb.metrics.MetricsSnapshot;
import com.cockroachlabs.crdb.multiregion.Mode;
import com.cockroachlabs.crdb.multiregion.MultiregionReport;
import com.cockroachlabs.crdb.testing.ConcurrencyResult;
import com.cockroachlabs.crdb.testing.ConcurrencyRunner;
import org.postgresql.ds.PGSimpleDataSource;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;

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
        verifyRegionalLayout(ds);
        Idea idea = repo.seed("Add dark mode to the ideas board");
        System.out.println("Seeded idea " + idea.id() + " — expecting " + EXPECTED + " upvotes\n");

        ConcurrencyRunner runner = new ConcurrencyRunner(WORKERS, ITERATIONS);

        // 1) No retry: conflicts are lost.
        repo.reset(idea.id());
        ConcurrencyResult plain = runner.run((w, i) -> service.upvoteWithoutRetry(idea.id()));
        report("WITHOUT retry (plain)", plain, repo.currentVotes(idea.id()));

        // tag::metrics-config[]
        // Enable metrics: attach a recorder to the core observe bus so every retry
        // the library makes is counted. timeTransaction additionally records each
        // transaction's duration and success/failure.
        CrdbMetrics metrics = CrdbMetrics.inMemory().register();
        // end::metrics-config[]

        // 2) With Crdb.tx retry, timed through the metrics facade: every vote counted.
        repo.reset(idea.id());
        ConcurrencyResult retry = runner.run((w, i) ->
                metrics.timeTransaction(() -> {
                    service.upvote(idea.id());
                    return null;
                }));
        report("WITH Crdb.tx retry", retry, repo.currentVotes(idea.id()));

        MetricsSnapshot snap = ((InMemoryMetricsRecorder) metrics.recorder()).snapshot();
        System.out.printf("%nmetrics: transactions=%d successes=%d retryAttempts=%d%n",
                snap.transactionCount(), snap.transactionSuccessCount(), snap.retryAttempts());
        metrics.unregister();

        // 3) Follower-read leaderboard (reads at follower_read_timestamp(), ~4.8s
        // stale, so freshly-written votes may not yet be visible).
        IdeaReadService reads = IdeaReadService.create(ds);
        System.out.println("\ntop ideas (follower read): " + reads.topIdeas(5));

        demonstrateChangefeed();
    }

    // tag::mr-check-usage[]
    /**
     * Startup locality check: confirm the live {@code regional_idea} table matches what
     * {@link RegionalIdea}'s {@code @RegionalByRow}/{@code @CrdbRegion} annotations declare.
     * {@code Mode.WARN} (the default) logs any mismatch with the exact fix-up SQL and returns
     * the report without throwing — only a contradiction fails startup. Use {@code Mode.VALIDATE}
     * to make any mismatch fatal.
     */
    private static void verifyRegionalLayout(DataSource ds) {
        MultiregionReport report = Crdb.verifyLocality(ds, Mode.WARN, RegionalIdea.class);
        System.out.println(report.isEmpty()
                ? "locality check: regional_idea matches @RegionalByRow\n"
                : report.format(Mode.WARN) + "\n");
    }
    // end::mr-check-usage[]

    // tag::cdc-register[]
    /**
     * Register the {@code idea} changefeed handler and feed it a synthetic webhook
     * event (a real deployment points a {@code CREATE CHANGEFEED ... INTO
     * 'webhook-...'} at a {@code ChangefeedWebhookHandler} mounted on an HttpServer).
     */
    private static void demonstrateChangefeed() {
        IdeaChangefeedHandler cdc = new IdeaChangefeedHandler();
        ChangefeedHandlerRegistry registry =
                new ChangefeedHandlerRegistry().register(IdeaChangefeedHandler.TABLE, cdc);

        CdcEvent synthetic = new CdcEvent(
                Operation.INSERT, IdeaChangefeedHandler.TABLE,
                List.of("00000000-0000-0000-0000-000000000001"),
                Map.of("id", "00000000-0000-0000-0000-000000000001",
                        "text", "Idea seen via CDC", "votes", 1),
                null, "1700000000.0");
        registry.dispatch(List.of(synthetic));
        System.out.println("cdc handled: " + cdc.handled());
    }
    // end::cdc-register[]

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
