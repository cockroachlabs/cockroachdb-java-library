package com.cockroachlabs.crdb.ai;

import java.util.Map;

/**
 * A single hit from a vector similarity search.
 *
 * @param id         the matched document id
 * @param content    the matched document content
 * @param metadata   the document's metadata (may be empty)
 * @param distance   the raw distance under the query's {@link DistanceMetric}
 * @param similarity a monotonic similarity score (higher = closer), derived via
 *                   {@link DistanceMetric#toSimilarity(double)}
 */
public record VectorSearchResult(
        String id,
        String content,
        Map<String, String> metadata,
        double distance,
        double similarity) {
}
