package com.cockroachlabs.crdb.cdc;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Routes decoded {@link CdcEvent}s to per-table {@link CdcEventHandler}s.
 *
 * <p>Register a handler per table with {@link #register(String, CdcEventHandler)}
 * and, optionally, a fallback with {@link #registerDefault(CdcEventHandler)} for
 * tables without a specific handler. {@link #dispatch(List)} groups a mixed batch
 * by table and invokes each table's handler once with that table's events.
 */
public final class ChangefeedHandlerRegistry {

    private final Map<String, CdcEventHandler> byTable = new ConcurrentHashMap<>();
    private volatile CdcEventHandler defaultHandler;

    /** Register (or replace) the handler for a table. */
    public ChangefeedHandlerRegistry register(String table, CdcEventHandler handler) {
        byTable.put(table, handler);
        return this;
    }

    /** Register a fallback handler used for events whose table has no specific handler. */
    public ChangefeedHandlerRegistry registerDefault(CdcEventHandler handler) {
        this.defaultHandler = handler;
        return this;
    }

    /** Remove the handler for a table. */
    public void unregister(String table) {
        byTable.remove(table);
    }

    /** Whether any handler (specific or default) can service the given table. */
    public boolean hasHandlerFor(String table) {
        return byTable.containsKey(table) || defaultHandler != null;
    }

    /**
     * Group {@code events} by table and invoke the matching handler once per
     * table. Events whose table has no specific handler go to the default
     * handler if one is registered; otherwise they are silently dropped.
     *
     * @return the number of distinct tables dispatched to a handler
     */
    public int dispatch(List<CdcEvent> events) {
        if (events == null || events.isEmpty()) {
            return 0;
        }
        Map<String, List<CdcEvent>> grouped = new LinkedHashMap<>();
        for (CdcEvent e : events) {
            grouped.computeIfAbsent(e.table(), t -> new ArrayList<>()).add(e);
        }
        int dispatched = 0;
        for (Map.Entry<String, List<CdcEvent>> entry : grouped.entrySet()) {
            CdcEventHandler handler = byTable.getOrDefault(entry.getKey(), defaultHandler);
            if (handler != null) {
                handler.handle(entry.getValue());
                dispatched++;
            }
        }
        return dispatched;
    }
}
