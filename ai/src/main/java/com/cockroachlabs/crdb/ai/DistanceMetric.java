package com.cockroachlabs.crdb.ai;

/**
 * A vector distance function. CockroachDB exposes three native distance
 * operators for its {@code VECTOR} type; each pairs with an operator class used
 * when building a vector index.
 */
public enum DistanceMetric {

    /** Euclidean (L2) distance — operator {@code <->}. */
    L2("<->", "vector_l2_ops"),

    /** Cosine distance — operator {@code <=>}. Best for text embeddings. */
    COSINE("<=>", "vector_cosine_ops"),

    /** Negative inner product — operator {@code <#>}. */
    INNER_PRODUCT("<#>", "vector_ip_ops");

    private final String operator;
    private final String opClass;

    DistanceMetric(String operator, String opClass) {
        this.operator = operator;
        this.opClass = opClass;
    }

    /** The CockroachDB SQL distance operator (e.g. {@code <=>}). */
    public String operator() {
        return operator;
    }

    /** The vector-index operator class (e.g. {@code vector_cosine_ops}). */
    public String opClass() {
        return opClass;
    }

    /**
     * Convert a raw distance into a monotonic similarity score where higher is
     * more similar: cosine → {@code 1 - d}, inner product → {@code -d}, L2 →
     * {@code 1 / (1 + d)}.
     */
    public double toSimilarity(double distance) {
        return switch (this) {
            case COSINE -> 1.0 - distance;
            case INNER_PRODUCT -> -distance;
            case L2 -> 1.0 / (1.0 + distance);
        };
    }
}
