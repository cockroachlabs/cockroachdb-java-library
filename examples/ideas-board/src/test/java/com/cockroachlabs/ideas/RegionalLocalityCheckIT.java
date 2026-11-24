package com.cockroachlabs.ideas;

import com.cockroachlabs.crdb.Crdb;
import com.cockroachlabs.crdb.multiregion.Mode;
import com.cockroachlabs.crdb.multiregion.MultiregionReport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.postgresql.ds.PGSimpleDataSource;

import java.sql.Connection;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves the startup locality check: a {@code regional_idea} table that is actually
 * REGIONAL BY ROW matches {@link RegionalIdea}'s {@code @RegionalByRow}/{@code @CrdbRegion}
 * annotations, so {@link Crdb#verifyLocality} reports no problems.
 *
 * <p><b>Gated on purpose</b> (same as {@link RegionalIdeaIT}): REGIONAL BY ROW needs a
 * multi-region database, which a single Testcontainers node does not have. This test SKIPS
 * unless {@code CRDB_MULTIREGION=true}; when enabled it reads an external cluster from
 * {@code CRDB_URL} (expected to already have {@code us-east1} and {@code us-west1} added
 * as regions). It never fails by default — it is skipped.
 */
@EnabledIfEnvironmentVariable(named = "CRDB_MULTIREGION", matches = "true")
class RegionalLocalityCheckIT {

    // tag::mr-check-report[]
    @Test
    void matchingSchemaPasses() throws Exception {
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setUrl(System.getenv("CRDB_URL"));

        // Bring the live table into the REGIONAL BY ROW shape the annotations declare.
        try (Connection c = ds.getConnection(); Statement s = c.createStatement()) {
            s.execute("DROP TABLE IF EXISTS regional_idea");
            for (String ddl : RegionalIdeaService.REGIONAL_BY_ROW_DDL.split(";")) {
                if (!ddl.isBlank()) {
                    s.execute(ddl);
                }
            }
        }

        MultiregionReport report = Crdb.verifyLocality(ds, Mode.WARN, RegionalIdea.class);

        assertTrue(report.isEmpty(),
                "a REGIONAL BY ROW regional_idea must satisfy @RegionalByRow, but: "
                        + report.format(Mode.WARN));
    }
    // end::mr-check-report[]
}
