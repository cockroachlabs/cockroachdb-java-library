package com.cockroachlabs.ideas;

import com.cockroachlabs.crdb.Crdb;
import com.cockroachlabs.crdb.annotations.FollowerRead;

import javax.sql.DataSource;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// tag::follower-read[]
/**
 * Leaderboard reads served from a follower via the library's {@code @FollowerRead}
 * API. The annotated method runs through a {@link Crdb#enhance enhanced} proxy,
 * which binds the follower-read staleness into the call context; the
 * {@code Crdb.tx} transaction then emits
 * {@code SET TRANSACTION AS OF SYSTEM TIME follower_read_timestamp()} as its first
 * statement, so the leaderboard query runs against a slightly-stale snapshot the
 * nearest replica can serve — no leaseholder round-trip.
 *
 * <p>This is an interface (plus a factory and a package-private impl) so
 * {@code Crdb.enhance} uses the JDK-dynamic-proxy strategy: the enhanced object is
 * the interface type and delegates to the original instance, which is the exact
 * idiom the library's own integration test uses for an annotated, DataSource-backed
 * service.
 */
public interface IdeaReadService {

    /**
     * The top ideas by vote count, read from a follower at
     * {@code follower_read_timestamp()} (about 4.8s stale). Because the read is
     * historical, freshly-inserted rows become visible only once wall-clock time
     * advances past the staleness window.
     */
    @FollowerRead
    List<Idea> topIdeas(int limit);

    /** Build a follower-read-enabled view over {@code ds}. */
    static IdeaReadService create(DataSource ds) {
        return Crdb.enhance((IdeaReadService) new Impl(ds));
    }

    /** JDBC implementation; the {@code @FollowerRead} on the concrete method is what the proxy reads. */
    final class Impl implements IdeaReadService {

        private final DataSource ds;

        Impl(DataSource ds) {
            this.ds = ds;
        }

        @Override
        @FollowerRead
        public List<Idea> topIdeas(int limit) {
            // Crdb.tx picks up the follower-read staleness bound by the proxy and
            // applies SET TRANSACTION AS OF SYSTEM TIME before this query runs.
            return Crdb.tx(ds, c -> {
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT id, text, votes FROM idea ORDER BY votes DESC, text ASC LIMIT ?")) {
                    ps.setInt(1, limit);
                    try (ResultSet rs = ps.executeQuery()) {
                        List<Idea> out = new ArrayList<>();
                        while (rs.next()) {
                            out.add(new Idea(
                                    (UUID) rs.getObject("id"),
                                    rs.getString("text"),
                                    rs.getInt("votes")));
                        }
                        return out;
                    }
                }
            });
        }
    }
}
// end::follower-read[]
