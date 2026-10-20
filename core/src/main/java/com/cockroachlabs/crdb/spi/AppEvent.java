package com.cockroachlabs.crdb.spi;

import java.util.Map;

/**
 * An application-level operational signal emitted to Cockroach Aegis (the
 * "exhaust"). The timestamp is supplied explicitly (never read from the wall
 * clock inside this type) so producers stay deterministic and testable.
 *
 * @param kind        event kind (e.g. {@code retry}, {@code retry_exhausted}, {@code circuit_open})
 * @param source      logical source of the event
 * @param attrs       arbitrary attributes (defensively copied; immutable)
 * @param epochMillis event time in epoch milliseconds, supplied by the caller
 */
public record AppEvent(String kind, String source, Map<String, Object> attrs, long epochMillis) {

    public AppEvent {
        attrs = attrs == null ? Map.of() : Map.copyOf(attrs);
    }

    /** Convenience factory that defensively copies {@code attrs}. */
    public static AppEvent of(String kind, String source, Map<String, Object> attrs, long epochMillis) {
        return new AppEvent(kind, source, attrs, epochMillis);
    }
}
