package com.cockroachlabs.crdb.continuum;

import com.cockroachlabs.crdb.Crdb;
import com.cockroachlabs.crdb.spi.StorageInsights;
import com.cockroachlabs.crdb.spi.TableStorage;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Live Cockroach Plenum storage-insights tests. */
class StorageInsightsIT {

    private static DataSource ds;
    private static boolean reachable;

    @BeforeAll
    static void connect() {
        ds = TestSupport.dataSource();
        reachable = TestSupport.reachable(ds);
    }

    @BeforeEach
    void requireCluster() {
        Assumptions.assumeTrue(reachable, "CockroachDB not reachable; skipping");
    }

    @Test
    void summaryIncludesUserTable() throws SQLException {
        TestSupport.exec(ds, "DROP TABLE IF EXISTS plenum_it");
        TestSupport.exec(ds, "CREATE TABLE plenum_it (id INT PRIMARY KEY, note STRING)");
        TestSupport.exec(ds, "INSERT INTO plenum_it VALUES (1,'a'),(2,'b'),(3,'c')");
        try {
            StorageInsights insights = Crdb.storage(ds); // resolves via ServiceLoader
            List<TableStorage> all = insights.summary();

            TableStorage t = all.stream()
                    .filter(x -> x.table().equals("plenum_it"))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("plenum_it not found in summary"));

            assertFalse(all.isEmpty());
            assertTrue(t.rows().isEmpty() || t.rows().getAsLong() >= 0, "rows >= 0 when present");
            assertTrue(t.bytes().isEmpty() || t.bytes().getAsLong() >= 0, "bytes >= 0 when present");
        } finally {
            TestSupport.exec(ds, "DROP TABLE IF EXISTS plenum_it");
        }
    }
}
