package com.cockroachlabs.ideas;

import com.cockroachlabs.crdb.ai.VectorDocument;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fast, no-Docker unit test for the one piece of genuinely unit-testable logic in the
 * semantic feature: {@link IdeaDoc#toVectorDocument()} mapping. The real
 * similarity-search behaviour (ordering, distances) needs a {@code VECTOR}-capable
 * database and is covered by the Testcontainers {@link SemanticSearchIT}.
 */
class SemanticSearchUnitTest {

    @Test
    void ideaDocMapsToVectorDocumentCarryingItsPrecomputedEmbedding() {
        float[] embedding = {0.1f, 0.2f, 0.3f};
        IdeaDoc doc = new IdeaDoc("dark-mode", "Dark mode toggle", embedding);

        VectorDocument vd = doc.toVectorDocument();

        assertEquals("dark-mode", vd.id());
        assertEquals("Dark mode toggle", vd.content());
        assertNotNull(vd.embedding(), "a precomputed embedding must be passed through, not regenerated");
        assertArrayEquals(embedding, vd.embedding(), "embedding values must survive the mapping");
        assertEquals(IdeaDoc.DIMENSIONS, vd.embedding().length, "embedding width must match the column");
        assertTrue(vd.metadata().isEmpty(), "no metadata is attached by the mapping");
    }
}
