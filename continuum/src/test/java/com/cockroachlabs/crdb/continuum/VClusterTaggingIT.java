package com.cockroachlabs.crdb.continuum;

import com.cockroachlabs.crdb.Crdb;
import com.cockroachlabs.crdb.annotations.VirtualCluster;
import com.cockroachlabs.crdb.annotations.Workload;
import com.cockroachlabs.crdb.annotations.Workload.Priority;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Live test that Cockroach vCluster annotations / facade tag the transaction's
 * session with application_name and the mapped QoS.
 */
class VClusterTaggingIT {

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

    /** Reads the session tags inside the current transaction. */
    private static String[] readTags(java.sql.Connection c) throws SQLException {
        String qos;
        String app;
        try (Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SHOW default_transaction_quality_of_service")) {
            rs.next();
            qos = rs.getString(1);
        }
        try (Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SHOW application_name")) {
            rs.next();
            app = rs.getString(1);
        }
        return new String[]{qos, app};
    }

    public interface Reporter {
        String[] report();
    }

    public static final class ReporterImpl implements Reporter {
        private final DataSource ds;

        public ReporterImpl(DataSource ds) {
            this.ds = ds;
        }

        @Override
        @VirtualCluster("analytics")
        @Workload(Priority.HIGH)
        public String[] report() {
            return Crdb.tx(ds, VClusterTaggingIT::readTags);
        }
    }

    @Test
    void annotationsTagSessionInsideTx() {
        Reporter reporter = Crdb.enhance((Reporter) new ReporterImpl(ds));
        String[] tags = reporter.report();
        assertEquals("critical", tags[0], "HIGH -> critical QoS");
        assertEquals("analytics", tags[1], "vCluster -> application_name");
    }

    @Test
    void facadeVclusterTagsSession() {
        String[] tags = Crdb.vcluster("reporting")
                .workload(Priority.LOW)
                .tx(ds, VClusterTaggingIT::readTags);
        assertEquals("background", tags[0], "LOW -> background QoS");
        assertEquals("reporting", tags[1], "vCluster -> application_name");
    }
}
