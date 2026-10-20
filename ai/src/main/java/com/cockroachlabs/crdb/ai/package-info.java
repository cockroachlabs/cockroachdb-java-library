/**
 * AI integration over CockroachDB's native {@code VECTOR} type.
 *
 * <p>{@link com.cockroachlabs.crdb.ai.VectorStore} is the high-level document
 * store (schema init, embedding, upsert, delete, similarity search);
 * {@link com.cockroachlabs.crdb.ai.VectorSearchTemplate} is the low-level search
 * engine over arbitrary tables. {@link com.cockroachlabs.crdb.ai.DistanceMetric}
 * maps L2/cosine/inner-product to CockroachDB's {@code <->}/{@code <=>}/{@code <#>}
 * operators and index operator classes.
 *
 * <p>Embeddings come from a pluggable
 * {@link com.cockroachlabs.crdb.ai.EmbeddingProvider} (with a dependency-free
 * {@link com.cockroachlabs.crdb.ai.HashingEmbeddingProvider} for tests/demos);
 * {@link com.cockroachlabs.crdb.ai.RagTemplate} combines retrieval with a
 * pluggable {@link com.cockroachlabs.crdb.ai.ChatModel} for RAG.
 */
package com.cockroachlabs.crdb.ai;
