package com.cockroachlabs.ideas;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.postgresql.ds.PGSimpleDataSource;

import java.sql.Connection;
import java.sql.Statement;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Proves region-scoped writes against a real <em>multi-region</em> CockroachDB cluster:
 * the {@code @Region}-annotated {@link RegionalIdeaService} methods land each row in the
 * right {@code crdb_region} of a REGIONAL BY ROW table.
 *
 * <p><b>Gated on purpose.</b> REGIONAL BY ROW needs a database with multiple regions,
 * which a single Testcontainers node does not have. This test therefore SKIPS unless
 * {@code CRDB_MULTIREGION=true}; when enabled it reads an external cluster from
 * {@code CRDB_URL} (expected to already have {@code us-east1} and {@code us-west1}
 * added as regions). It never fails by default — it is skipped.
 */
@EnabledIfEnvironmentVariable(named = "CRDB_MULTIREGION", matches = "true")
class RegionalIdeaIT {

    // tag::region-test[]
    @Test
    void writesHomeRowsInTheAnnotatedRegion() throws Exception {
        String url = System.getenv("CRDB_URL");
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setUrl(url);

        // The external multi-region database must already have the regions added:
        //   ALTER DATABASE <db> ADD REGION 'us-east1'; ADD REGION 'us-west1';
        try (Connection c = ds.getConnection(); Statement s = c.createStatement()) {
            s.execute("DROP TABLE IF EXISTS regional_idea");
            for (String ddl : RegionalIdeaService.REGIONAL_BY_ROW_DDL.split(";")) {
                if (!ddl.isBlank()) {
                    s.execute(ddl);
                }
            }
        }

        RegionalIdeaService svc = RegionalIdeaService.create(ds);

        UUID east = UUID.randomUUID();
        UUID west = UUID.randomUUID();
        svc.addInUsEast(east, "Home me in the east");
        svc.addInUsWest(west, "Home me in the west");

        assertEquals("us-east1", svc.regionOf(east), "@Region(us-east1) must land the row in us-east1");
        assertEquals("us-west1", svc.regionOf(west), "@Region(us-west1) must land the row in us-west1");
    }
    // end::region-test[]
}
