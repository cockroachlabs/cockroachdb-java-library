package com.cockroachlabs.ideas;

import com.cockroachlabs.crdb.annotations.CrdbRegion;
import com.cockroachlabs.crdb.annotations.RegionalByRow;

import java.util.UUID;

// tag::mr-check-entity[]
// tag::regional-entity[]
/**
 * An idea pinned to a CockroachDB region via REGIONAL BY ROW. {@code region} is the
 * hidden {@code crdb_region} column the library writes from the call context, so the
 * row's home (and its low-latency leaseholder) follows the writer's region.
 *
 * <p>The annotations declare the intended locality so {@code Crdb.verifyLocality}
 * can confirm, at startup, that the live table actually matches. {@code table} and
 * {@code regionColumn} are set because the simple class name folds to
 * {@code regionalidea} (not {@code regional_idea}) and the region column is
 * CockroachDB's implicit, hidden {@code crdb_region} rather than a column named
 * after the {@link CrdbRegion} field.
 */
@RegionalByRow(table = "regional_idea", regionColumn = "crdb_region")
public record RegionalIdea(UUID id, String text, @CrdbRegion String region) {
}
// end::regional-entity[]
// end::mr-check-entity[]
