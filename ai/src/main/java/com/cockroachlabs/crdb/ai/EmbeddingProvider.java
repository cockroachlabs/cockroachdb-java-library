package com.cockroachlabs.crdb.ai;

/**
 * Converts text into a vector embedding for storage in a CockroachDB
 * {@code VECTOR} column. Implementations wrap a specific provider (OpenAI,
 * Ollama, a local model, etc.).
 *
 * <p>{@link #dimensions()} must equal the {@code VECTOR(dims)} column width used
 * by the {@link VectorStore}/{@link VectorSearchTemplate}.
 */
public interface EmbeddingProvider {

    /** Embed a single piece of text. The returned array length must equal {@link #dimensions()}. */
    float[] embed(String text);

    /** The dimensionality of vectors produced by this provider. */
    int dimensions();
}
