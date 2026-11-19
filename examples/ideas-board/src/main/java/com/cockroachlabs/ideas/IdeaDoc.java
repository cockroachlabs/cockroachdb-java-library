package com.cockroachlabs.ideas;

import com.cockroachlabs.crdb.ai.VectorDocument;

import java.util.Map;

// tag::vector-entity[]
/**
 * An idea with a small fixed-dimension embedding, stored in a CockroachDB native
 * {@code VECTOR} column so similar ideas can be found by vector distance.
 *
 * <p>The embedding here is a deterministic {@code float[3]} supplied by the caller
 * (no model): that keeps the semantic-search integration test reproducible on a
 * single node. In a real app the vector would come from an {@code EmbeddingProvider}
 * (OpenAI, Ollama, a local model) of whatever width the column declares.
 */
public record IdeaDoc(String id, String text, float[] embedding) {

    /** Embedding width; must match the {@code VECTOR(DIMENSIONS)} column. */
    public static final int DIMENSIONS = 3;

    /**
     * Map to the AI module's {@link VectorDocument}, carrying the precomputed
     * embedding (non-null, so the store never consults an embedding model).
     */
    public VectorDocument toVectorDocument() {
        return new VectorDocument(id, text, embedding, Map.of());
    }
}
// end::vector-entity[]
