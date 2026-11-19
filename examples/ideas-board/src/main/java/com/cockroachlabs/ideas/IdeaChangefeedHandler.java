package com.cockroachlabs.ideas;

import com.cockroachlabs.crdb.cdc.CdcEvent;
import com.cockroachlabs.crdb.cdc.CdcEventHandler;
import com.cockroachlabs.crdb.cdc.Operation;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CopyOnWriteArrayList;

// tag::cdc-handler[]
/**
 * A {@link CdcEventHandler} for the {@code idea} table. CockroachDB's changefeed
 * webhook delivers batches of decoded {@link CdcEvent}s; this handler turns each
 * insert/update into an {@link Idea} and tracks deletes, so an app can mirror the
 * ideas board into a cache, search index, or audit log as rows change.
 *
 * <p>Thread-safe: a changefeed webhook can deliver batches concurrently.
 */
public final class IdeaChangefeedHandler implements CdcEventHandler {

    /** The table this handler is registered for. */
    public static final String TABLE = "idea";

    private final List<Idea> handled = new CopyOnWriteArrayList<>();
    private final AtomicInteger deletes = new AtomicInteger();

    @Override
    public void handle(List<CdcEvent> events) {
        for (CdcEvent event : events) {
            if (event.operation() == Operation.DELETE) {
                deletes.incrementAndGet(); // after-image is null on a delete
                continue;
            }
            handled.add(toIdea(event.after()));
        }
    }

    /** Project a changefeed {@code after} image onto an {@link Idea}. */
    static Idea toIdea(Map<String, Object> after) {
        return new Idea(
                UUID.fromString(String.valueOf(after.get("id"))),
                String.valueOf(after.get("text")),
                ((Number) after.get("votes")).intValue()); // JSON numbers decode as Number
    }

    /** Ideas seen so far from insert/update events, in arrival order. */
    public List<Idea> handled() {
        return List.copyOf(handled);
    }

    /** Count of delete events seen. */
    public int deletes() {
        return deletes.get();
    }
}
// end::cdc-handler[]
