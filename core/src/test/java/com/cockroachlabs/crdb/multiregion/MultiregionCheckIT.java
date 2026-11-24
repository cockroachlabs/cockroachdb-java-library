package com.cockroachlabs.crdb.multiregion;

import java.sql.Connection;
import java.sql.Statement;

import javax.sql.DataSource;

import com.cockroachlabs.crdb.Crdb;
import com.cockroachlabs.crdb.annotations.GlobalTable;
import com.cockroachlabs.crdb.annotations.RegionalByRow;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.postgresql.ds.PGSimpleDataSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves {@link Crdb#verifyLocality} against a real <em>multi-region</em> CockroachDB cluster:
 * a {@code @GlobalTable}/{@code @RegionalByRow} entity whose table has the matching locality
 * passes, and a {@code @GlobalTable} entity pointing at a {@code REGIONAL BY ROW} table is a
 * contradiction that throws.
 *
 * <p><b>Gated on purpose.</b> GLOBAL / REGIONAL BY ROW localities need a database with a primary
 * region, which a single Testcontainers node does not have. This test SKIPS unless
 * {@code CRDB_MULTIREGION=true}; when enabled it reads an external cluster from {@code CRDB_URL}
 * (expected to already have its regions added). It is run by the {@code examples-it-multiregion}
 * workflow against the 3-region docker-compose cluster.
 */
@EnabledIfEnvironmentVariable(named = "CRDB_MULTIREGION", matches = "true")
class MultiregionCheckIT {

    @GlobalTable(table = "mr_global_it")
    static class GlobalEntity {
    }

    @RegionalByRow(table = "mr_rbr_it")
    static class RbrEntity {
    }

    /** Points @GlobalTable at the REGIONAL BY ROW table — a contradiction. */
    @GlobalTable(table = "mr_rbr_it")
    static class ContradictingEntity {
    }

    private static DataSource dataSource() {
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setUrl(System.getenv("CRDB_URL"));
        return ds;
    }

    private static void seed(DataSource ds) throws Exception {
        try (Connection c = ds.getConnection(); Statement s = c.createStatement()) {
            s.execute("DROP TABLE IF EXISTS mr_global_it");
            s.execute("DROP TABLE IF EXISTS mr_rbr_it");
            s.execute("CREATE TABLE mr_global_it (id UUID PRIMARY KEY DEFAULT gen_random_uuid())");
            s.execute("ALTER TABLE mr_global_it SET LOCALITY GLOBAL");
            s.execute("CREATE TABLE mr_rbr_it (id UUID PRIMARY KEY DEFAULT gen_random_uuid())");
            s.execute("ALTER TABLE mr_rbr_it SET LOCALITY REGIONAL BY ROW");
        }
    }

    @Test
    void matchingAnnotationsPass() throws Exception {
        DataSource ds = dataSource();
        seed(ds);
        MultiregionReport report = Crdb.verifyLocality(ds, GlobalEntity.class, RbrEntity.class);
        assertTrue(report.isEmpty(),
                "@GlobalTable on a GLOBAL table and @RegionalByRow on a REGIONAL BY ROW table must pass, but: "
                        + report.format(Mode.WARN));
    }

    @Test
    void contradictionThrowsInWarnMode() throws Exception {
        DataSource ds = dataSource();
        seed(ds);
        // @GlobalTable on a REGIONAL BY ROW table is a contradiction: it throws even in WARN.
        assertThrows(IllegalStateException.class,
                () -> Crdb.verifyLocality(ds, ContradictingEntity.class));
    }

    @Test
    void validateThrowsOnMissingTable() throws Exception {
        DataSource ds = dataSource();
        try (Connection c = ds.getConnection(); Statement s = c.createStatement()) {
            s.execute("DROP TABLE IF EXISTS mr_global_it");
        }
        assertThrows(IllegalStateException.class,
                () -> Crdb.verifyLocality(ds, Mode.VALIDATE, GlobalEntity.class));
    }

    @Test
    void noneModeIsOff() throws Exception {
        DataSource ds = dataSource();
        MultiregionReport report = Crdb.verifyLocality(ds, Mode.NONE, ContradictingEntity.class);
        assertTrue(report.isEmpty(), "NONE mode must not connect or report");
        assertFalse(report.hasContradictions());
    }
}
