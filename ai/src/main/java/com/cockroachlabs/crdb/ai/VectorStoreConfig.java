package com.cockroachlabs.crdb.ai;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Immutable configuration for a {@link VectorStore}: table and column names,
 * distance metric, vector dimensionality, and vector-index options for
 * CockroachDB v26.2+.
 *
 * <p>Build one with {@link #builder()}. All identifiers are validated to guard
 * against injection, since they are interpolated into DDL/DML.
 */
public final class VectorStoreConfig {

    private static final Pattern IDENT = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_$]*$");

    private final String tableName;
    private final String idColumn;
    private final String contentColumn;
    private final String embeddingColumn;
    private final String metadataColumn;
    private final DistanceMetric distanceMetric;
    private final int dimensions;
    private final int beamSize;
    private final boolean createVectorIndex;
    private final List<String> prefixColumns;

    private VectorStoreConfig(Builder b) {
        this.tableName = validate(b.tableName, "tableName");
        this.idColumn = validate(b.idColumn, "idColumn");
        this.contentColumn = validate(b.contentColumn, "contentColumn");
        this.embeddingColumn = validate(b.embeddingColumn, "embeddingColumn");
        this.metadataColumn = validate(b.metadataColumn, "metadataColumn");
        this.distanceMetric = b.distanceMetric;
        this.dimensions = b.dimensions;
        this.beamSize = b.beamSize;
        this.createVectorIndex = b.createVectorIndex;
        this.prefixColumns = List.copyOf(b.prefixColumns);
        for (String col : this.prefixColumns) {
            validate(col, "prefixColumn");
        }
        if (dimensions <= 0) {
            throw new IllegalArgumentException("dimensions must be positive");
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public String tableName() {
        return tableName;
    }

    public String idColumn() {
        return idColumn;
    }

    public String contentColumn() {
        return contentColumn;
    }

    public String embeddingColumn() {
        return embeddingColumn;
    }

    public String metadataColumn() {
        return metadataColumn;
    }

    public DistanceMetric distanceMetric() {
        return distanceMetric;
    }

    public int dimensions() {
        return dimensions;
    }

    public int beamSize() {
        return beamSize;
    }

    public boolean createVectorIndex() {
        return createVectorIndex;
    }

    public List<String> prefixColumns() {
        return prefixColumns;
    }

    private static String validate(String ident, String what) {
        if (ident == null || !IDENT.matcher(ident).matches()) {
            throw new IllegalArgumentException("invalid " + what + " identifier: " + ident);
        }
        return ident;
    }

    /** Builder for {@link VectorStoreConfig}. */
    public static final class Builder {
        private String tableName = "vector_store";
        private String idColumn = "id";
        private String contentColumn = "content";
        private String embeddingColumn = "embedding";
        private String metadataColumn = "metadata";
        private DistanceMetric distanceMetric = DistanceMetric.COSINE;
        private int dimensions;
        private int beamSize;
        private boolean createVectorIndex;
        private List<String> prefixColumns = List.of();

        public Builder tableName(String tableName) {
            this.tableName = tableName;
            return this;
        }

        public Builder idColumn(String idColumn) {
            this.idColumn = idColumn;
            return this;
        }

        public Builder contentColumn(String contentColumn) {
            this.contentColumn = contentColumn;
            return this;
        }

        public Builder embeddingColumn(String embeddingColumn) {
            this.embeddingColumn = embeddingColumn;
            return this;
        }

        public Builder metadataColumn(String metadataColumn) {
            this.metadataColumn = metadataColumn;
            return this;
        }

        public Builder distanceMetric(DistanceMetric distanceMetric) {
            this.distanceMetric = distanceMetric;
            return this;
        }

        public Builder dimensions(int dimensions) {
            this.dimensions = dimensions;
            return this;
        }

        /** C-SPANN search beam size (0 = CockroachDB default). Higher = more accurate, slower. */
        public Builder beamSize(int beamSize) {
            this.beamSize = beamSize;
            return this;
        }

        /** Create a {@code VECTOR INDEX} during schema init (CockroachDB v26.2+). */
        public Builder createVectorIndex(boolean createVectorIndex) {
            this.createVectorIndex = createVectorIndex;
            return this;
        }

        /** Prefix (partition) columns preceding the embedding column in the vector index. */
        public Builder prefixColumns(String... prefixColumns) {
            this.prefixColumns = List.of(prefixColumns);
            return this;
        }

        public VectorStoreConfig build() {
            return new VectorStoreConfig(this);
        }
    }
}
