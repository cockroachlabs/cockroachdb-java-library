package com.cockroachlabs.crdb.ai;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Live test: store embeddings in a CockroachDB {@code VECTOR} column and search
 * them back with the native distance operators. Uses the deterministic
 * {@link HashingEmbeddingProvider} so no external embedding API is needed.
 * Skipped when no node is reachable.
 */
class VectorStoreIT {

    private static final String URL = System.getenv().getOrDefault(
            "CRDB_URL", "jdbc:postgresql://localhost:26257/parity_java?user=root&sslmode=disable");
    private static final int DIMS = 32;

    private DataSource ds;
    private String table;
    private VectorStore store;

    @BeforeEach
    void setUp() {
        ds = dataSource();
        assumeTrue(reachable(ds), "CockroachDB not reachable at " + URL + " — skipping vector IT");
        table = "parity_vec_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        VectorStoreConfig config = VectorStoreConfig.builder()
                .tableName(table)
                .dimensions(DIMS)
                .distanceMetric(DistanceMetric.COSINE)
                .build();
        store = new VectorStore(ds, new HashingEmbeddingProvider(DIMS), config);
        store.initializeSchema();
        store.add(
                VectorDocument.of("cat", "cats are feline pets that purr", Map.of("kind", "animal")),
                VectorDocument.of("dog", "dogs are loyal canine pets that bark", Map.of("kind", "animal")),
                VectorDocument.of("db", "cockroachdb is a distributed sql database", Map.of("kind", "tech")));
    }

    @AfterEach
    void tearDown() {
        if (store != null && table != null) {
            try {
                exec("DROP TABLE IF EXISTS " + table);
            } catch (SQLException ignored) {
                // best effort
            }
        }
    }

    @Test
    void storeAndSimilaritySearchRoundTrip() {
        List<VectorSearchResult> hits = store.similaritySearch("feline pet cat that purr", 2);
        assertFalse(hits.isEmpty(), "expected at least one hit");
        assertEquals("cat", hits.get(0).id(), "nearest neighbor should be the cat document");
        assertEquals("animal", hits.get(0).metadata().get("kind"), "metadata should round-trip");
        assertTrue(hits.get(0).similarity() >= hits.get(hits.size() - 1).similarity(),
                "results should be ordered by descending similarity");
    }

    @Test
    void metadataFilterNarrowsResults() {
        List<VectorSearchResult> techOnly = store.similaritySearch(
                "distributed sql database", 5, "metadata->>'kind' = 'tech'");
        assertEquals(1, techOnly.size());
        assertEquals("db", techOnly.get(0).id());
    }

    @Test
    void deleteRemovesDocument() {
        assertTrue(store.delete("dog"));
        List<VectorSearchResult> hits = store.similaritySearch("loyal canine pet", 5);
        assertTrue(hits.stream().noneMatch(h -> h.id().equals("dog")), "deleted doc must not reappear");
    }

    @Test
    void lowLevelTemplateSearchesSameTable() {
        VectorSearchTemplate template = new VectorSearchTemplate(ds);
        float[] q = new HashingEmbeddingProvider(DIMS).embed("distributed sql database");
        List<Map<String, Object>> rows = template.search(
                VectorSearchTemplate.SearchRequest.builder()
                        .table(table)
                        .vectorColumn("embedding")
                        .queryVector(q)
                        .metric(DistanceMetric.COSINE)
                        .columns("id", "content")
                        .limit(1)
                        .build());
        assertEquals(1, rows.size());
        assertEquals("db", rows.get(0).get("id"));
        assertTrue(rows.get(0).containsKey("distance"));
    }

    @Test
    void ragTemplateGroundsAnswerInRetrievedDocs() {
        RagTemplate rag = new RagTemplate(store, new EchoChatModel());
        String answer = rag.answer("distributed sql database", 1);
        assertTrue(answer.contains("cockroachdb is a distributed sql database"),
                "RAG answer should include the retrieved passage; got: " + answer);
    }

    private void exec(String sql) throws SQLException {
        try (Connection c = ds.getConnection(); Statement s = c.createStatement()) {
            s.execute(sql);
        }
    }

    private static DataSource dataSource() {
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setUrl(URL);
        return ds;
    }

    private static boolean reachable(DataSource ds) {
        try (Connection c = ds.getConnection()) {
            return c.isValid(3);
        } catch (SQLException e) {
            return false;
        }
    }
}
