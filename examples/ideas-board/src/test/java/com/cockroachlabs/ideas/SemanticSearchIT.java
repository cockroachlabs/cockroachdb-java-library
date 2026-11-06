package com.cockroachlabs.ideas;

import com.cockroachlabs.crdb.ai.VectorSearchResult;
import com.cockroachlabs.crdb.testing.CockroachHarness;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves semantic search end to end against a real CockroachDB node: four ideas with
 * deterministic {@code float[3]} embeddings are stored in a native {@code VECTOR}
 * column, and a query vector near one of them returns the ideas ordered closest-first
 * by cosine distance.
 *
 * <p><b>Vector enablement:</b> the {@link CockroachHarness} has no vector-enable
 * helper, and its default image ({@code cockroachdb/cockroach:v24.1.5}) predates the
 * native {@code VECTOR} type. Vector is therefore enabled by running against a
 * v25.2+ image via {@link CockroachHarness#container(String)} — overridable with the
 * {@code CRDB_VECTOR_IMAGE} env var. Brute-force KNN (no {@code CREATE VECTOR INDEX})
 * needs only the type and the {@code <=>} operator, so no feature flag is required.
 * If the chosen image genuinely lacks the {@code VECTOR} type, the test SKIPS (via
 * {@link Assumptions}) rather than faking a pass.
 *
 * <p>Requires Docker at test runtime.
 */
class SemanticSearchIT {

    private static final String IMAGE =
            System.getenv().getOrDefault("CRDB_VECTOR_IMAGE", "cockroachdb/cockroach:latest-v25.2");

    // tag::vector-it[]
    @Test
    void findSimilarReturnsNearestIdeaFirst() {
        try (CockroachHarness harness = CockroachHarness.container(IMAGE).start()) {
            PGSimpleDataSource ds = new PGSimpleDataSource();
            ds.setUrl(harness.jdbcUrl());
            ds.setUser("root");

            SemanticIdeaService semantic = new SemanticIdeaService(ds);
            try {
                semantic.initialize();
            } catch (RuntimeException e) {
                // Honest gate: if this image has no native VECTOR type, skip (don't fake it).
                Assumptions.assumeTrue(!mentionsVectorUnsupported(e),
                        "VECTOR type unsupported on image " + IMAGE + ": " + e.getMessage());
                throw e;
            }

            // Four deterministic 3-dim vectors pointing in distinct directions.
            semantic.store(
                    new IdeaDoc("dark-mode", "Dark mode toggle", new float[] {1.0f, 0.0f, 0.0f}),
                    new IdeaDoc("mobile", "Mobile dark theme", new float[] {0.8f, 0.2f, 0.0f}),
                    new IdeaDoc("search", "Full-text search", new float[] {0.0f, 1.0f, 0.0f}),
                    new IdeaDoc("export", "CSV export", new float[] {0.0f, 0.0f, 1.0f}));

            // Query near "dark-mode"; second-nearest is "mobile", then "search", then "export".
            List<VectorSearchResult> hits = semantic.findSimilar(new float[] {0.95f, 0.05f, 0.0f}, 4);

            assertEquals(4, hits.size(), "all four ideas must be returned");
            assertEquals("dark-mode", hits.get(0).id(), "nearest idea must sort first");
            assertEquals("mobile", hits.get(1).id(), "second-nearest idea must sort second");
            assertEquals("search", hits.get(2).id());
            assertEquals("export", hits.get(3).id(), "most-distant idea must sort last");

            // Distances must be non-decreasing (closest-first), and the hero hit is very close.
            for (int i = 1; i < hits.size(); i++) {
                assertTrue(hits.get(i - 1).distance() <= hits.get(i).distance(),
                        "results must be ordered by distance ascending");
            }
            assertTrue(hits.get(0).distance() < 0.01,
                    "the query sits almost on dark-mode, so its distance must be tiny");
        }
    }
    // end::vector-it[]

    private static boolean mentionsVectorUnsupported(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            String m = t.getMessage();
            if (m != null) {
                String lower = m.toLowerCase();
                if (lower.contains("vector") || lower.contains("type \"vector\" does not exist")) {
                    return true;
                }
            }
        }
        return false;
    }
}
