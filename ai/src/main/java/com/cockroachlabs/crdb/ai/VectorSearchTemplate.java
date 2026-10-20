package com.cockroachlabs.crdb.ai;

import com.cockroachlabs.crdb.anomaly.AnomalyClassifier;
import com.cockroachlabs.crdb.anomaly.CrdbException;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Low-level vector similarity search over an arbitrary CockroachDB table using
 * the native {@code VECTOR} operators. Unlike {@link VectorStore} it makes no
 * assumptions about the table's shape: you name the table, the vector column, the
 * columns to return, and an optional filter, and it returns rows plus a
 * {@code distance}.
 */
public final class VectorSearchTemplate {

    private static final Pattern IDENT = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_$.]*$");

    private final DataSource dataSource;

    public VectorSearchTemplate(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    /**
     * Create a vector table (and optionally a v26.2+ vector index).
     *
     * @param table           table name
     * @param embeddingColumn the {@code VECTOR} column name
     * @param dims            vector dimensionality
     * @param metric          distance metric for the index (null = no index)
     * @param prefixColumns   optional partition columns preceding the vector column in the index
     */
    public void initSchema(String table, String embeddingColumn, int dims,
                           DistanceMetric metric, String... prefixColumns) {
        requireIdent(table, "table");
        requireIdent(embeddingColumn, "embeddingColumn");
        if (dims <= 0) {
            throw new IllegalArgumentException("dims must be positive");
        }
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.execute("CREATE TABLE IF NOT EXISTS " + table + " ("
                    + "id TEXT PRIMARY KEY, content TEXT, metadata JSONB DEFAULT '{}'::jsonb, "
                    + embeddingColumn + " VECTOR(" + dims + "))");
            if (metric != null) {
                StringBuilder cols = new StringBuilder();
                for (String p : prefixColumns) {
                    requireIdent(p, "prefixColumn");
                    cols.append(p).append(", ");
                }
                cols.append(embeddingColumn).append(' ').append(metric.opClass());
                s.execute("CREATE VECTOR INDEX IF NOT EXISTS " + table + "_vec_idx ON "
                        + table + " (" + cols + ")");
            }
        } catch (SQLException e) {
            throw new CrdbException(AnomalyClassifier.classify(e));
        }
    }

    /**
     * Run a similarity search. Each returned map contains the requested columns
     * (or all columns when none are specified) plus a {@code distance} entry.
     */
    public List<Map<String, Object>> search(SearchRequest req) {
        req.validate();
        String op = req.metric.operator();
        String lit = "'" + Vectors.toLiteral(req.queryVector) + "'::vector";
        String cols = req.columns.isEmpty() ? "*" : String.join(", ", req.columns);
        StringBuilder sql = new StringBuilder("SELECT ").append(cols)
                .append(", ").append(req.vectorColumn).append(' ').append(op).append(' ').append(lit)
                .append(" AS distance FROM ").append(req.table);
        if (req.filter != null && !req.filter.isBlank()) {
            sql.append(" WHERE ").append(req.filter);
        }
        sql.append(" ORDER BY ").append(req.vectorColumn).append(' ').append(op).append(' ').append(lit)
                .append(" LIMIT ").append(req.limit);

        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            if (req.beamSize > 0) {
                try (Statement s = c.createStatement()) {
                    s.execute("SET LOCAL vector_search_beam_size = " + req.beamSize);
                }
            }
            List<Map<String, Object>> out = new ArrayList<>();
            try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery(sql.toString())) {
                ResultSetMetaData md = rs.getMetaData();
                int n = md.getColumnCount();
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 1; i <= n; i++) {
                        row.put(md.getColumnLabel(i), rs.getObject(i));
                    }
                    out.add(row);
                }
            }
            c.commit();
            return out;
        } catch (SQLException e) {
            throw new CrdbException(AnomalyClassifier.classify(e));
        }
    }

    private static void requireIdent(String ident, String what) {
        if (ident == null || !IDENT.matcher(ident).matches()) {
            throw new IllegalArgumentException("invalid " + what + " identifier: " + ident);
        }
    }

    /** A vector search request; build with {@link #builder()}. */
    public static final class SearchRequest {
        private final String table;
        private final String vectorColumn;
        private final float[] queryVector;
        private final DistanceMetric metric;
        private final int limit;
        private final String filter;
        private final int beamSize;
        private final List<String> columns;

        private SearchRequest(Builder b) {
            this.table = b.table;
            this.vectorColumn = b.vectorColumn;
            this.queryVector = b.queryVector;
            this.metric = b.metric;
            this.limit = b.limit;
            this.filter = b.filter;
            this.beamSize = b.beamSize;
            this.columns = List.copyOf(b.columns);
        }

        public static Builder builder() {
            return new Builder();
        }

        private void validate() {
            requireIdent(table, "table");
            requireIdent(vectorColumn, "vectorColumn");
            if (queryVector == null || queryVector.length == 0) {
                throw new IllegalArgumentException("queryVector must be non-empty");
            }
            if (limit <= 0) {
                throw new IllegalArgumentException("limit must be positive");
            }
            for (String col : columns) {
                requireIdent(col, "column");
            }
        }

        /** Builder for {@link SearchRequest}. */
        public static final class Builder {
            private String table;
            private String vectorColumn = "embedding";
            private float[] queryVector;
            private DistanceMetric metric = DistanceMetric.COSINE;
            private int limit = 10;
            private String filter;
            private int beamSize;
            private List<String> columns = List.of();

            public Builder table(String table) {
                this.table = table;
                return this;
            }

            public Builder vectorColumn(String vectorColumn) {
                this.vectorColumn = vectorColumn;
                return this;
            }

            public Builder queryVector(float[] queryVector) {
                this.queryVector = queryVector;
                return this;
            }

            public Builder metric(DistanceMetric metric) {
                this.metric = metric;
                return this;
            }

            public Builder limit(int limit) {
                this.limit = limit;
                return this;
            }

            public Builder filter(String filter) {
                this.filter = filter;
                return this;
            }

            public Builder beamSize(int beamSize) {
                this.beamSize = beamSize;
                return this;
            }

            public Builder columns(String... columns) {
                this.columns = List.of(columns);
                return this;
            }

            public SearchRequest build() {
                return new SearchRequest(this);
            }
        }
    }
}
