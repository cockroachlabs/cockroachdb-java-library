package com.cockroachlabs.crdb.ai;

import java.util.Locale;

/**
 * A deterministic, dependency-free {@link EmbeddingProvider} that hashes
 * whitespace-delimited tokens into a fixed-dimension bag-of-words vector and
 * L2-normalizes the result.
 *
 * <p>It is not a semantic model, but it is stable and reproducible: identical
 * text always yields the identical vector, and texts sharing tokens land close
 * together under cosine distance. This makes it ideal for unit and integration
 * tests and for local demos where calling a real embedding API is undesirable.
 */
public final class HashingEmbeddingProvider implements EmbeddingProvider {

    /** Default dimensionality. */
    public static final int DEFAULT_DIMENSIONS = 64;

    private final int dimensions;

    public HashingEmbeddingProvider() {
        this(DEFAULT_DIMENSIONS);
    }

    public HashingEmbeddingProvider(int dimensions) {
        if (dimensions <= 0) {
            throw new IllegalArgumentException("dimensions must be positive");
        }
        this.dimensions = dimensions;
    }

    @Override
    public float[] embed(String text) {
        float[] v = new float[dimensions];
        if (text == null || text.isBlank()) {
            return v;
        }
        for (String token : text.toLowerCase(Locale.ROOT).split("\\W+")) {
            if (token.isEmpty()) {
                continue;
            }
            int bucket = Math.floorMod(token.hashCode(), dimensions);
            // Sign bucket keeps some tokens from purely reinforcing one another.
            int sign = (token.hashCode() & 1) == 0 ? 1 : -1;
            v[bucket] += sign;
        }
        normalize(v);
        return v;
    }

    @Override
    public int dimensions() {
        return dimensions;
    }

    private static void normalize(float[] v) {
        double sumSq = 0;
        for (float f : v) {
            sumSq += (double) f * f;
        }
        if (sumSq == 0) {
            return;
        }
        double norm = Math.sqrt(sumSq);
        for (int i = 0; i < v.length; i++) {
            v[i] = (float) (v[i] / norm);
        }
    }
}
