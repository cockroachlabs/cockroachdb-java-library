package com.cockroachlabs.ideas;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

// tag::repo[]
/**
 * Plain-JDBC data access for {@link Idea}. No Spring, no JPA — just a
 * {@link DataSource} and {@link PreparedStatement}s, so this works in any JDBC
 * app.
 *
 * <p>The read-modify-write helpers ({@link #findById} / {@link #updateVotes})
 * take an in-transaction {@link Connection} so the caller controls isolation and
 * commit; the convenience helpers ({@link #currentVotes} / {@link #reset}) open
 * their own short-lived connection.
 */
public final class IdeaRepository {

    private final DataSource ds;

    public IdeaRepository(DataSource ds) {
        this.ds = ds;
    }

    /** Create the single-table schema (idempotent). */
    public void createSchema() {
        try (Connection c = ds.getConnection(); Statement s = c.createStatement()) {
            s.execute("""
                CREATE TABLE IF NOT EXISTS idea (
                    id    UUID PRIMARY KEY,
                    text  STRING NOT NULL,
                    votes INT NOT NULL DEFAULT 0
                )""");
        } catch (SQLException e) {
            throw new RuntimeException("schema creation failed", e);
        }
    }

    /** Insert one idea with zero votes and return it. */
    public Idea seed(String text) {
        Idea idea = new Idea(UUID.randomUUID(), text, 0);
        try (Connection c = ds.getConnection();
             PreparedStatement ps =
                     c.prepareStatement("INSERT INTO idea (id, text, votes) VALUES (?, ?, 0)")) {
            ps.setObject(1, idea.id());
            ps.setString(2, idea.text());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("seed failed", e);
        }
        return idea;
    }

    /** Read one idea on an in-transaction connection (the "read" of read-modify-write). */
    public Idea findById(Connection c, UUID id) throws SQLException {
        try (PreparedStatement ps =
                     c.prepareStatement("SELECT id, text, votes FROM idea WHERE id = ?")) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new SQLException("idea not found: " + id);
                }
                return new Idea((UUID) rs.getObject("id"), rs.getString("text"), rs.getInt("votes"));
            }
        }
    }

    /** Write the vote count on an in-transaction connection (the "write"). */
    public void updateVotes(Connection c, UUID id, int votes) throws SQLException {
        try (PreparedStatement ps =
                     c.prepareStatement("UPDATE idea SET votes = ? WHERE id = ?")) {
            ps.setInt(1, votes);
            ps.setObject(2, id);
            ps.executeUpdate();
        }
    }

    /** The current vote count, on its own connection (for assertions / printing). */
    public int currentVotes(UUID id) {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT votes FROM idea WHERE id = ?")) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : -1;
            }
        } catch (SQLException e) {
            throw new RuntimeException("currentVotes failed", e);
        }
    }

    /** Reset the vote count to zero (between the two contention runs). */
    public void reset(UUID id) {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement("UPDATE idea SET votes = 0 WHERE id = ?")) {
            ps.setObject(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("reset failed", e);
        }
    }
}
// end::repo[]
