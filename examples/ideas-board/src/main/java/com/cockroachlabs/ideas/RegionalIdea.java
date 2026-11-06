package com.cockroachlabs.ideas;

import java.util.UUID;

// tag::regional-entity[]
/**
 * An idea pinned to a CockroachDB region via REGIONAL BY ROW. {@code region} is the
 * hidden {@code crdb_region} column the library writes from the call context, so the
 * row's home (and its low-latency leaseholder) follows the writer's region.
 */
public record RegionalIdea(UUID id, String text, String region) {
}
// end::regional-entity[]
