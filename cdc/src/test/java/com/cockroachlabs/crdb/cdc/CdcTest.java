package com.cockroachlabs.crdb.cdc;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CdcTest {

    // ---- ChangefeedBuilder ---------------------------------------------------

    @Test
    void buildsCreateChangefeedSqlWithOrderedOptions() {
        String sql = ChangefeedBuilder.forTables("orders", "payments")
                .intoWebhook("https://sink.example/cdc")
                .format("json")
                .updated()
                .diff()
                .resolved("10s")
                .toSql();

        assertEquals(
                "CREATE CHANGEFEED FOR TABLE orders, payments INTO 'webhook-https://sink.example/cdc' "
                        + "WITH format = 'json', updated, diff, resolved = '10s'",
                sql);
    }

    @Test
    void webhookPrefixNotDoubled() {
        String sql = ChangefeedBuilder.forTables("t").intoWebhook("webhook-https://x/y").toSql();
        assertTrue(sql.contains("INTO 'webhook-https://x/y'"));
    }

    @Test
    void rejectsInvalidTableIdentifier() {
        assertThrows(IllegalArgumentException.class,
                () -> ChangefeedBuilder.forTables("orders; DROP TABLE users"));
    }

    @Test
    void requiresTableAndSink() {
        assertThrows(IllegalStateException.class,
                () -> ChangefeedBuilder.forTables("t").toSql());
    }

    @Test
    void escapesOptionValues() {
        String sql = ChangefeedBuilder.forTables("t").into("kafka://k").cursor("it's").toSql();
        assertTrue(sql.contains("cursor = 'it''s'"), sql);
    }

    // ---- Operation classification -------------------------------------------

    @Test
    void classifiesOperations() {
        assertEquals(Operation.INSERT, Operation.fromImages(null, java.util.Map.of("id", 1)));
        assertEquals(Operation.UPDATE, Operation.fromImages(java.util.Map.of("id", 1), java.util.Map.of("id", 1)));
        assertEquals(Operation.DELETE, Operation.fromImages(java.util.Map.of("id", 1), null));
    }

    // ---- Decoder -------------------------------------------------------------

    @Test
    void decodesWrappedEnvelopeIntoEvents() {
        String json = """
                {"payload":[
                  {"after":{"id":1,"v":10},"key":[1],"topic":"orders","updated":"1790.1"},
                  {"after":null,"key":[2],"topic":"orders"}
                ],"length":2}""";
        CdcEventDecoder decoder = new CdcEventDecoder();
        CdcEnvelope env = decoder.decode(json);
        assertFalse(env.isResolved());
        assertEquals(2, env.length());

        List<CdcEvent> events = decoder.toEvents(env);
        assertEquals(2, events.size());
        assertEquals(Operation.INSERT, events.get(0).operation());
        assertEquals("orders", events.get(0).table());
        assertEquals(10, events.get(0).after().get("v"));
        assertEquals(Operation.DELETE, events.get(1).operation());
        assertNull(events.get(1).after());
    }

    @Test
    void recognizesResolvedEnvelope() {
        CdcEnvelope env = new CdcEventDecoder().decode("{\"resolved\":\"1790.5\"}");
        assertTrue(env.isResolved());
        assertEquals("1790.5", env.resolved());
    }

    // ---- Registry dispatch ---------------------------------------------------

    @Test
    void registryGroupsEventsByTable() {
        List<CdcEvent> ordersSeen = new ArrayList<>();
        List<CdcEvent> paymentsSeen = new ArrayList<>();
        ChangefeedHandlerRegistry registry = new ChangefeedHandlerRegistry()
                .register("orders", ordersSeen::addAll)
                .register("payments", paymentsSeen::addAll);

        List<CdcEvent> batch = List.of(
                new CdcEvent(Operation.INSERT, "orders", List.of(1), java.util.Map.of("v", 1), null, null),
                new CdcEvent(Operation.INSERT, "payments", List.of(2), java.util.Map.of("v", 2), null, null),
                new CdcEvent(Operation.UPDATE, "orders", List.of(1), java.util.Map.of("v", 3), null, null));

        int tables = registry.dispatch(batch);
        assertEquals(2, tables);
        assertEquals(2, ordersSeen.size());
        assertEquals(1, paymentsSeen.size());
    }

    @Test
    void registryFallsBackToDefaultHandler() {
        List<CdcEvent> seen = new ArrayList<>();
        ChangefeedHandlerRegistry registry = new ChangefeedHandlerRegistry().registerDefault(seen::addAll);
        int tables = registry.dispatch(List.of(
                new CdcEvent(Operation.INSERT, "unmapped", List.of(1), java.util.Map.of(), null, null)));
        assertEquals(1, tables);
        assertEquals(1, seen.size());
    }

    // ---- Webhook handler (no HTTP) ------------------------------------------

    @Test
    void webhookHandlerDispatchesDataAndResolved() {
        List<CdcEvent> seen = new ArrayList<>();
        AtomicReference<String> resolved = new AtomicReference<>();
        ChangefeedHandlerRegistry registry = new ChangefeedHandlerRegistry().register("orders", seen::addAll);
        ChangefeedWebhookHandler handler = new ChangefeedWebhookHandler(registry)
                .onResolved(resolved::set);

        int dispatched = handler.handleBody(
                "{\"payload\":[{\"after\":{\"id\":1},\"key\":[1],\"topic\":\"orders\"}],\"length\":1}");
        assertEquals(1, dispatched);
        assertEquals(1, seen.size());

        int resolvedDispatched = handler.handleBody("{\"resolved\":\"1790.9\"}");
        assertEquals(0, resolvedDispatched);
        assertEquals("1790.9", resolved.get());
    }
}
