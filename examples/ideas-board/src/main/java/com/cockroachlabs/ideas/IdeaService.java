package com.cockroachlabs.ideas;

import com.cockroachlabs.crdb.Crdb;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;

/**
 * Upvotes an idea two ways so you can watch serialization conflicts bite — and
 * then watch the library make them disappear.
 */
public final class IdeaService {

    private final DataSource ds;
    private final IdeaRepository repo;

    public IdeaService(DataSource ds, IdeaRepository repo) {
        this.ds = ds;
        this.repo = repo;
    }

    // tag::plain[]
    /**
     * Upvote with NO retry. A plain SERIALIZABLE read-modify-write: under
     * contention CockroachDB raises SQLSTATE 40001 and this method just lets it
     * propagate, so concurrent upvotes are lost.
     */
    public void upvoteWithoutRetry(UUID id) {
        try (Connection c = ds.getConnection()) {
            c.setAutoCommit(false);
            // SERIALIZABLE is required for 40001 to surface on a read-modify-write.
            c.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
            try {
                Idea idea = repo.findById(c, id);
                repo.updateVotes(c, id, idea.votes() + 1);
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw new RuntimeException(e);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
    // end::plain[]

    // tag::retry[]
    /**
     * Upvote WITH automatic retry. {@code Crdb.tx} runs the same
     * read-modify-write in a SERIALIZABLE transaction and replays the whole unit
     * on a 40001 conflict, so every upvote eventually lands.
     */
    public void upvote(UUID id) {
        Crdb.tx(ds, c -> {
            Idea idea = repo.findById(c, id);
            repo.updateVotes(c, id, idea.votes() + 1);
            return null;
        });
    }
    // end::retry[]
}
