package com.cockroachlabs.crdb.ai;

import java.util.Map;

/**
 * A document to store in a {@link VectorStore}.
 *
 * @param id        a stable unique id; if null/blank, the store generates a UUID
 * @param content   the text content
 * @param embedding the precomputed embedding, or {@code null} to have the store
 *                  generate one from {@code content} via its {@link EmbeddingProvider}
 * @param metadata  optional string metadata stored as JSONB (may be null)
 */
public record VectorDocument(
        String id,
        String content,
        float[] embedding,
        Map<String, String> metadata) {

    /** A document whose embedding will be generated on insert. */
    public static VectorDocument of(String id, String content) {
        return new VectorDocument(id, content, null, Map.of());
    }

    /** A document with content and metadata; embedding generated on insert. */
    public static VectorDocument of(String id, String content, Map<String, String> metadata) {
        return new VectorDocument(id, content, null, metadata);
    }
}
