package com.cockroachlabs.ideas;

import java.util.UUID;

// tag::entity[]
/**
 * An idea on the board. {@code votes} is the hotly-contended column: many
 * upvotes race to increment the same row, which is exactly where CockroachDB's
 * SERIALIZABLE isolation raises 40001 and the library's retry earns its keep.
 */
public record Idea(UUID id, String text, int votes) {
}
// end::entity[]
