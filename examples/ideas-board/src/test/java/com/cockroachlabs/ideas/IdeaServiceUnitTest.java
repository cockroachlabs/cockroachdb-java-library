package com.cockroachlabs.ideas;

import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Fast, no-Docker unit test for {@link IdeaService#upvoteWithoutRetry}.
 *
 * <p>This mocks the JDBC seam ({@link DataSource} / {@link Connection}) and the
 * {@link IdeaRepository}, so it asserts only the <em>service's call/logic</em>:
 * that it reads via {@code findById}, writes {@code votes + 1} via
 * {@code updateVotes}, selects SERIALIZABLE isolation, and commits — with no
 * real database.
 *
 * <p>The real SQLSTATE 40001 contention behavior and the {@code Crdb.tx} retry
 * (the hero feature) are covered by the Testcontainers integration test
 * {@link IdeaServiceConcurrencyTest}; nothing here needs a container.
 */
class IdeaServiceUnitTest {

    // tag::unit-test[]
    @Test
    void upvoteWithoutRetryReadsIncrementsAndCommits() throws Exception {
        UUID id = UUID.randomUUID();

        DataSource ds = mock(DataSource.class);
        Connection conn = mock(Connection.class);
        IdeaRepository repo = mock(IdeaRepository.class);

        when(ds.getConnection()).thenReturn(conn);
        when(repo.findById(conn, id)).thenReturn(new Idea(id, "Ship it", 41));

        new IdeaService(ds, repo).upvoteWithoutRetry(id);

        verify(repo).findById(conn, id);
        verify(repo).updateVotes(conn, id, 42); // read 41, write 41 + 1
        verify(conn).setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
        verify(conn).commit();
    }
    // end::unit-test[]

    @Test
    void ideaRecordExposesItsFields() {
        UUID id = UUID.randomUUID();
        Idea idea = new Idea(id, "Add dark mode", 7);

        assertEquals(id, idea.id());
        assertEquals("Add dark mode", idea.text());
        assertEquals(7, idea.votes());
    }
}
