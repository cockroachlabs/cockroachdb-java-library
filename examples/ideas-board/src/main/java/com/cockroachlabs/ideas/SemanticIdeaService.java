package com.cockroachlabs.ideas;

import com.cockroachlabs.crdb.ai.DistanceMetric;
import com.cockroachlabs.crdb.ai.HashingEmbeddingProvider;
import com.cockroachlabs.crdb.ai.VectorSearchResult;
import com.cockroachlabs.crdb.ai.VectorStore;
import com.cockroachlabs.crdb.ai.VectorStoreConfig;

import javax.sql.DataSource;
import java.util.Arrays;
import java.util.List;

// tag::vector-search[]
/**
 * Semantic search over ideas, backed by the AI module's {@link VectorStore} on a
 * CockroachDB native {@code VECTOR} column.
 *
 * <p>{@code store(...)} upserts {@link IdeaDoc}s (each carrying a precomputed
 * embedding) and {@code findSimilar(queryVector, k)} returns the nearest docs,
 * ordered closest-first, via the configured {@link DistanceMetric}'s SQL distance
 * operator ({@code <=>} for cosine).
 *
 * <p>Uses brute-force KNN (no {@code CREATE VECTOR INDEX}), which needs only the
 * {@code VECTOR} type and distance operator — available on CockroachDB v25.2+ with
 * no index/feature flag — so the search is deterministic on a single test node.
 */
public final class SemanticIdeaService {

    /** The backing table; shared with the integration test for assertions. */
    public static final String TABLE = "idea_doc";

    private final VectorStore store;

    public SemanticIdeaService(DataSource ds) {
        VectorStoreConfig config = VectorStoreConfig.builder()
                .tableName(TABLE)
                .dimensions(IdeaDoc.DIMENSIONS)
                .distanceMetric(DistanceMetric.COSINE)
                .createVectorIndex(false) // brute-force KNN; no C-SPANN index needed
                .build();
        // Embeddings are precomputed per IdeaDoc, so the provider is only here to
        // satisfy the store's non-null embedder contract and dimension check; it is
        // never consulted for stored or queried vectors.
        this.store = new VectorStore(ds, new HashingEmbeddingProvider(IdeaDoc.DIMENSIONS), config);
    }

    /** Create the backing {@code VECTOR} table if absent. Safe to call repeatedly. */
    public void initialize() {
        store.initializeSchema();
    }

    /** Upsert ideas with their precomputed embeddings. */
    public void store(IdeaDoc... docs) {
        store.add(Arrays.stream(docs).map(IdeaDoc::toVectorDocument).toList());
    }

    /** The {@code k} ideas nearest to {@code queryVector}, closest-first. */
    public List<VectorSearchResult> findSimilar(float[] queryVector, int k) {
        return store.similaritySearch(queryVector, k, null);
    }
}
// end::vector-search[]
