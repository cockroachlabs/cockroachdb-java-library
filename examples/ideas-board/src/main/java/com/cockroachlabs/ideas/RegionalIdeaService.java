package com.cockroachlabs.ideas;

import com.cockroachlabs.crdb.Crdb;
import com.cockroachlabs.crdb.annotations.Region;
import com.cockroachlabs.crdb.context.CrdbContext;

import javax.sql.DataSource;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;

/**
 * Region-scoped writes to a REGIONAL BY ROW {@code regional_idea} table, using the
 * library's {@code @Region} API.
 *
 * <p>Same enhanced-proxy idiom as {@link IdeaReadService}: {@code Crdb.enhance}
 * returns a JDK dynamic proxy over an interface; a {@code @Region}-annotated method
 * binds the region into {@link CrdbContext} for the duration of the call. The impl
 * reads {@code CrdbContext.region()} and writes it into the hidden {@code crdb_region}
 * column, so the row homes in that region without the caller hand-threading it.
 *
 * <p>REGIONAL BY ROW requires a multi-region database (multiple {@code ADD REGION}s),
 * which a single node does not have — so the proof ({@link RegionalIdeaIT}) is gated
 * on a real multi-region cluster and skips by default.
 */
public interface RegionalIdeaService {

    // tag::region-config[]
    /**
     * Enablement DDL. On a multi-region database (created with
     * {@code ALTER DATABASE ... ADD REGION ...}) this makes the table REGIONAL BY ROW,
     * adding the hidden {@code crdb_region} column the service writes.
     */
    String REGIONAL_BY_ROW_DDL = """
            CREATE TABLE IF NOT EXISTS regional_idea (id UUID PRIMARY KEY, text STRING NOT NULL);
            ALTER TABLE regional_idea SET LOCALITY REGIONAL BY ROW;
            """;
    // end::region-config[]

    // tag::inregion-usage[]
    /** Store an idea homed in {@code us-east1} (bound into the context by {@code @Region}). */
    @Region("us-east1")
    void addInUsEast(UUID id, String text);

    /** Store an idea homed in {@code us-west1}. */
    @Region("us-west1")
    void addInUsWest(UUID id, String text);
    // end::inregion-usage[]

    /** The {@code crdb_region} a row actually landed in, if present. */
    String regionOf(UUID id);

    /** Build a region-aware view over {@code ds}. */
    static RegionalIdeaService create(DataSource ds) {
        return Crdb.enhance((RegionalIdeaService) new Impl(ds));
    }

    /** JDBC implementation; the {@code @Region} on the concrete method is what the proxy reads. */
    final class Impl implements RegionalIdeaService {

        private final DataSource ds;

        Impl(DataSource ds) {
            this.ds = ds;
        }

        @Override
        @Region("us-east1")
        public void addInUsEast(UUID id, String text) {
            insertInBoundRegion(id, text);
        }

        @Override
        @Region("us-west1")
        public void addInUsWest(UUID id, String text) {
            insertInBoundRegion(id, text);
        }

        /**
         * Write the row into the region the proxy bound into {@link CrdbContext}.
         * {@code crdb_region} is set explicitly so the REGIONAL BY ROW row homes
         * where the {@code @Region} annotation says, not where the gateway happens to be.
         */
        private void insertInBoundRegion(UUID id, String text) {
            String region = CrdbContext.region().orElseThrow(
                    () -> new IllegalStateException("no region bound; call via the enhanced proxy"));
            Crdb.tx(ds, c -> {
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO regional_idea (id, text, crdb_region) VALUES (?, ?, ?)")) {
                    ps.setObject(1, id);
                    ps.setString(2, text);
                    ps.setString(3, region);
                    ps.executeUpdate();
                }
                return null;
            });
        }

        @Override
        public String regionOf(UUID id) {
            return Crdb.tx(ds, c -> {
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT crdb_region::STRING FROM regional_idea WHERE id = ?")) {
                    ps.setObject(1, id);
                    try (ResultSet rs = ps.executeQuery()) {
                        return rs.next() ? rs.getString(1) : null;
                    }
                }
            });
        }
    }
}
