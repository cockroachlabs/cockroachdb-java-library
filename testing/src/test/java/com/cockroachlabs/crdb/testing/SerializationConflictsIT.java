package com.cockroachlabs.crdb.testing;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Live test: the conflict workload actually provokes CockroachDB serialization
 * failures under the concurrency runner, and the counter's final value matches
 * the number of successful (committed) increments. Skipped when no node is
 * reachable.
 */
class SerializationConflictsIT {

    private static final String URL = System.getenv().getOrDefault(
            "CRDB_URL", "jdbc:postgresql://localhost:26257/parity_java?user=root&sslmode=disable");

    private CockroachHarness harness;
    private DataSource ds;
    private String table;

    @BeforeEach
    void setUp() {
        harness = CockroachHarness.external(URL);
        assumeTrue(harness.reachable(), "CockroachDB not reachable at " + URL + " — skipping conflicts IT");
        ds = harness.dataSource();
        table = "parity_conf_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        SerializationConflicts.createCounter(ds, table, "k");
    }

    @Test
    void concurrentIncrementsProduceConflictsAndConsistentCounter() {
        int workers = 8;
        int iterations = 15;
        ConcurrencyResult result = new ConcurrencyRunner(workers, iterations)
                .run(SerializationConflicts.incrementTask(ds, table, "k"));

        // The read-modify-write workload without retry must generate at least one
        // 40001 conflict under this level of contention.
        assertTrue(result.failures() > 0,
                "expected serialization conflicts under contention; result=" + result);

        // Each committed increment adds exactly 1, so the counter equals successes.
        long counter = SerializationConflicts.readCounter(ds, table, "k");
        assertEquals(result.successes(), counter,
                "counter must equal the number of committed increments");
    }
}
