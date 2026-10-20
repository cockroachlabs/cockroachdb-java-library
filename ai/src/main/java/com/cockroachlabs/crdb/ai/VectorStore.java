package com.cockroachlabs.crdb.ai;

import com.cockroachlabs.crdb.anomaly.AnomalyClassifier;
import com.cockroachlabs.crdb.anomaly.CrdbException;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * High-level document store backed by CockroachDB's native {@code VECTOR} type.
 *
 * <p>Handles schema creation, embedding generation (via an {@link EmbeddingProvider}),
 * upserts, deletes, and similarity search. For low-level control over arbitrary
 * tables use {@link VectorSearchTemplate} instead.
 *
 * <p>Vector values are interpolated as numeric literals (safe: they contain only
 * digits, signs, dots, and commas); ids, content, and metadata are bound as
 * statement parameters.
 */
public final class VectorStore {

    private final DataSource dataSource;
    private final EmbeddingProvider embedder;
    private final VectorStoreConfig config;

    public VectorStore(DataSource dataSource, EmbeddingProvider embedder, VectorStoreConfig config) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.embedder = Objects.requireNonNull(embedder, "embedder");
        this.config = Objects.requireNonNull(config, "config");
        if (embedder.dimensions() != config.dimensions()) {
            throw new IllegalArgumentException(
                    "embedder dimensions (" + embedder.dimensions()
                            + ") != config dimensions (" + config.dimensions() + ")");
        }
    }

    /** The configuration in effect. */
    public VectorStoreConfig config() {
        return config;
    }

    /**
     * Create the backing table (and optionally a vector index) if absent. Safe to
     * call repeatedly.
     */
    public void initializeSchema() {
        String create = "CREATE TABLE IF NOT EXISTS " + config.tableName() + " ("
                + config.idColumn() + " TEXT PRIMARY KEY, "
                + config.contentColumn() + " TEXT, "
                + config.metadataColumn() + " JSONB DEFAULT '{}'::jsonb, "
                + config.embeddingColumn() + " VECTOR(" + config.dimensions() + "))";
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.execute(create);
            if (config.createVectorIndex()) {
                StringBuilder cols = new StringBuilder();
                for (String prefix : config.prefixColumns()) {
                    cols.append(prefix).append(", ");
                }
                cols.append(config.embeddingColumn()).append(' ').append(config.distanceMetric().opClass());
                s.execute("CREATE VECTOR INDEX IF NOT EXISTS " + config.tableName() + "_vec_idx ON "
                        + config.tableName() + " (" + cols + ")");
            }
        } catch (SQLException e) {
            throw new CrdbException(AnomalyClassifier.classify(e));
        }
    }

    /** Upsert one or more documents, generating embeddings for any that lack one. */
    public void add(VectorDocument... docs) {
        add(List.of(docs));
    }

    /** Upsert a list of documents, generating embeddings for any that lack one. */
    public void add(List<VectorDocument> docs) {
        if (docs.isEmpty()) {
            return;
        }
        String sql = "INSERT INTO " + config.tableName() + " ("
                + config.idColumn() + ", " + config.contentColumn() + ", "
                + config.metadataColumn() + ", " + config.embeddingColumn() + ") "
                + "VALUES (?, ?, ?::jsonb, %s::vector) "
                + "ON CONFLICT (" + config.idColumn() + ") DO UPDATE SET "
                + config.contentColumn() + " = EXCLUDED." + config.contentColumn() + ", "
                + config.metadataColumn() + " = EXCLUDED." + config.metadataColumn() + ", "
                + config.embeddingColumn() + " = EXCLUDED." + config.embeddingColumn();
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            for (VectorDocument doc : docs) {
                float[] embedding = doc.embedding() != null ? doc.embedding() : embedder.embed(doc.content());
                String id = (doc.id() == null || doc.id().isBlank()) ? UUID.randomUUID().toString() : doc.id();
                String rendered = String.format(sql, "'" + Vectors.toLiteral(embedding) + "'");
                try (PreparedStatement ps = c.prepareStatement(rendered)) {
                    ps.setString(1, id);
                    ps.setString(2, doc.content());
                    ps.setString(3, Json.encodeObject(doc.metadata()));
                    ps.executeUpdate();
                }
            }
            c.commit();
        } catch (SQLException e) {
            throw new CrdbException(AnomalyClassifier.classify(e));
        }
    }

    /** Delete a document by id. Returns true if a row was removed. */
    public boolean delete(String id) {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "DELETE FROM " + config.tableName() + " WHERE " + config.idColumn() + " = ?")) {
            ps.setString(1, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new CrdbException(AnomalyClassifier.classify(e));
        }
    }

    /** Similarity search using the query text (embedded via the provider). */
    public List<VectorSearchResult> similaritySearch(String queryText, int topK) {
        return similaritySearch(embedder.embed(queryText), topK, null);
    }

    /** Similarity search with an optional SQL metadata filter (e.g. {@code metadata->>'lang' = 'en'}). */
    public List<VectorSearchResult> similaritySearch(String queryText, int topK, String filter) {
        return similaritySearch(embedder.embed(queryText), topK, filter);
    }

    /** Similarity search using a precomputed query vector and optional SQL filter. */
    public List<VectorSearchResult> similaritySearch(float[] queryVector, int topK, String filter) {
        String op = config.distanceMetric().operator();
        String lit = "'" + Vectors.toLiteral(queryVector) + "'::vector";
        StringBuilder sql = new StringBuilder("SELECT ")
                .append(config.idColumn()).append(", ")
                .append(config.contentColumn()).append(", ")
                .append(config.metadataColumn()).append(", ")
                .append(config.embeddingColumn()).append(' ').append(op).append(' ').append(lit)
                .append(" AS distance FROM ").append(config.tableName());
        if (filter != null && !filter.isBlank()) {
            sql.append(" WHERE ").append(filter);
        }
        sql.append(" ORDER BY ").append(config.embeddingColumn()).append(' ').append(op).append(' ').append(lit)
                .append(" LIMIT ").append(topK);

        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            if (config.beamSize() > 0) {
                try (Statement s = c.createStatement()) {
                    s.execute("SET LOCAL vector_search_beam_size = " + config.beamSize());
                }
            }
            List<VectorSearchResult> results = new ArrayList<>();
            try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery(sql.toString())) {
                while (rs.next()) {
                    double distance = rs.getDouble("distance");
                    Map<String, String> metadata = Json.parseObject(rs.getString(config.metadataColumn()));
                    results.add(new VectorSearchResult(
                            rs.getString(config.idColumn()),
                            rs.getString(config.contentColumn()),
                            metadata,
                            distance,
                            config.distanceMetric().toSimilarity(distance)));
                }
            }
            c.commit();
            return results;
        } catch (SQLException e) {
            throw new CrdbException(AnomalyClassifier.classify(e));
        }
    }
}
