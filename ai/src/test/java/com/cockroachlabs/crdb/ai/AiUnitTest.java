package com.cockroachlabs.crdb.ai;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiUnitTest {

    @Test
    void vectorLiteralFormatting() {
        assertEquals("[]", Vectors.toLiteral(new float[0]));
        assertEquals("[1,2,3]", Vectors.toLiteral(new float[]{1f, 2f, 3f}));
        assertTrue(Vectors.toLiteral(new float[]{0.5f, -0.25f}).startsWith("[0.5,-0.25"));
    }

    @Test
    void distanceMetricOperatorsAndOpClasses() {
        assertEquals("<->", DistanceMetric.L2.operator());
        assertEquals("<=>", DistanceMetric.COSINE.operator());
        assertEquals("<#>", DistanceMetric.INNER_PRODUCT.operator());
        assertEquals("vector_cosine_ops", DistanceMetric.COSINE.opClass());
        assertEquals("vector_l2_ops", DistanceMetric.L2.opClass());
        assertEquals("vector_ip_ops", DistanceMetric.INNER_PRODUCT.opClass());
    }

    @Test
    void similarityIsMonotonic() {
        // Cosine: smaller distance => larger similarity.
        assertTrue(DistanceMetric.COSINE.toSimilarity(0.1) > DistanceMetric.COSINE.toSimilarity(0.9));
        assertTrue(DistanceMetric.L2.toSimilarity(0.0) > DistanceMetric.L2.toSimilarity(5.0));
        assertEquals(-3.0, DistanceMetric.INNER_PRODUCT.toSimilarity(3.0));
    }

    @Test
    void hashingEmbedderIsDeterministicAndNormalized() {
        HashingEmbeddingProvider e = new HashingEmbeddingProvider(32);
        assertEquals(32, e.dimensions());
        float[] a = e.embed("the quick brown fox");
        float[] b = e.embed("the quick brown fox");
        assertArrayEquals(a, b, "same text must yield identical vectors");

        double norm = 0;
        for (float f : a) {
            norm += (double) f * f;
        }
        assertEquals(1.0, Math.sqrt(norm), 1e-5, "vector should be L2-normalized");

        // Distinct text yields a different vector.
        assertTrue(!java.util.Arrays.equals(a, e.embed("something entirely different words")));
    }

    @Test
    void jsonRoundTripsFlatStringMap() {
        Map<String, String> meta = Map.of("lang", "en", "topic", "db's & \"quotes\"");
        String json = Json.encodeObject(meta);
        Map<String, String> parsed = Json.parseObject(json);
        assertEquals("en", parsed.get("lang"));
        assertEquals("db's & \"quotes\"", parsed.get("topic"));
    }

    @Test
    void jsonParsesCockroachStyleObject() {
        // JSONB rendered with spaces and sorted keys, mixed scalar types.
        Map<String, String> parsed = Json.parseObject("{\"a\": \"x\", \"n\": 3, \"b\": true, \"z\": null}");
        assertEquals("x", parsed.get("a"));
        assertEquals("3", parsed.get("n"));
        assertEquals("true", parsed.get("b"));
        assertTrue(parsed.containsKey("z"));
    }

    @Test
    void vectorStoreConfigRejectsBadIdentifiers() {
        assertThrows(IllegalArgumentException.class,
                () -> VectorStoreConfig.builder().dimensions(8).tableName("t; DROP").build());
        assertThrows(IllegalArgumentException.class,
                () -> VectorStoreConfig.builder().dimensions(0).build());
    }

    @Test
    void templateRequiresDataSource() {
        assertThrows(NullPointerException.class, () -> new VectorSearchTemplate(null));
    }

    @Test
    void echoChatModelSummarizesContext() {
        String answer = new EchoChatModel().generate("q?", List.of("passage one", "passage two"));
        assertTrue(answer.contains("q?"));
        assertTrue(answer.contains("passage one"));
        assertTrue(answer.contains("2 passages"));
    }
}
