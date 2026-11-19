package com.cockroachlabs.ideas;

import com.cockroachlabs.crdb.cdc.CdcEvent;
import com.cockroachlabs.crdb.cdc.CdcEventDecoder;
import com.cockroachlabs.crdb.cdc.ChangefeedHandlerRegistry;
import com.cockroachlabs.crdb.cdc.Operation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fast, no-Docker unit test for {@link IdeaChangefeedHandler}. CDC processing is
 * pure logic — classify the event, project the {@code after} image onto an
 * {@link Idea} — so it needs no live changefeed. The library ships no CDC
 * simulator, so we exercise the handler two ways: directly with a constructed
 * {@link CdcEvent}, and through the library's real decode-and-dispatch path by
 * feeding a synthetic changefeed webhook envelope.
 */
class IdeaCdcUnitTest {

    // tag::cdc-unit-test[]
    @Test
    void handlerProjectsInsertEventOntoIdeaAndTracksDeletes() {
        IdeaChangefeedHandler handler = new IdeaChangefeedHandler();
        UUID id = UUID.randomUUID();

        // A synthetic insert: after-image present, no before-image.
        CdcEvent insert = new CdcEvent(
                Operation.INSERT, IdeaChangefeedHandler.TABLE, List.of(id.toString()),
                Map.of("id", id.toString(), "text", "Add dark mode", "votes", 7),
                null, "1700000000.0000000000");
        // A synthetic delete: after-image is null.
        CdcEvent delete = new CdcEvent(
                Operation.DELETE, IdeaChangefeedHandler.TABLE, List.of(id.toString()),
                null, null, "1700000001.0000000000");

        handler.handle(List.of(insert, delete));

        assertEquals(1, handler.handled().size(), "insert must be projected onto an Idea");
        Idea seen = handler.handled().get(0);
        assertEquals(id, seen.id());
        assertEquals("Add dark mode", seen.text());
        assertEquals(7, seen.votes());
        assertEquals(1, handler.deletes(), "delete must be counted, not projected");
    }

    @Test
    void handlerProcessesAWebhookEnvelopeThroughDecodeAndDispatch() {
        IdeaChangefeedHandler handler = new IdeaChangefeedHandler();
        ChangefeedHandlerRegistry registry =
                new ChangefeedHandlerRegistry().register(IdeaChangefeedHandler.TABLE, handler);

        UUID id = UUID.randomUUID();
        // The exact 'wrapped' JSON envelope CockroachDB's webhook sink emits.
        String envelope = """
                {"payload":[
                  {"after":{"id":"%s","text":"Full-text search","votes":12},"key":["%s"],"topic":"idea","updated":"1700000002.0"}
                ],"length":1}""".formatted(id, id);

        List<CdcEvent> events = new CdcEventDecoder().decodeEvents(envelope);
        int tablesDispatched = registry.dispatch(events);

        assertEquals(1, tablesDispatched, "the idea table's handler must be invoked");
        assertEquals(1, handler.handled().size());
        Idea seen = handler.handled().get(0);
        assertEquals(id, seen.id());
        assertEquals("Full-text search", seen.text());
        assertEquals(12, seen.votes());
        assertTrue(handler.deletes() == 0);
    }
    // end::cdc-unit-test[]
}
