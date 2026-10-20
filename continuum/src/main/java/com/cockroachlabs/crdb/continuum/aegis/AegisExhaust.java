package com.cockroachlabs.crdb.continuum.aegis;

import com.cockroachlabs.crdb.observe.CrdbEventListener;
import com.cockroachlabs.crdb.observe.CrdbEvents;
import com.cockroachlabs.crdb.spi.AegisClient;
import com.cockroachlabs.crdb.spi.AppEvent;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.LongSupplier;

/**
 * Feeds application-level operational signals (retries, retry exhaustion, circuit
 * trips) into a Cockroach Aegis {@link AegisClient} as {@link AppEvent}s.
 *
 * <p>It attaches to the core {@link CrdbEvents} bus as a {@link CrdbEventListener},
 * so coupling stays light: with Aegis disabled nothing registers and the engine
 * pays only for atomic counters. The timestamp source is injectable for
 * deterministic tests.
 */
public final class AegisExhaust implements CrdbEventListener, AutoCloseable {

    private final AegisClient client;
    private final LongSupplier clock;
    private final String source;

    public AegisExhaust(AegisClient client) {
        this(client, System::currentTimeMillis, "cockroachdb-java-library");
    }

    public AegisExhaust(AegisClient client, LongSupplier clock, String source) {
        this.client = client;
        this.clock = clock;
        this.source = source;
    }

    /** Register on the core event bus. Returns {@code this} for chaining. */
    public AegisExhaust register() {
        CrdbEvents.addListener(this);
        return this;
    }

    /** Deregister from the core event bus. */
    @Override
    public void close() {
        CrdbEvents.removeListener(this);
    }

    @Override
    public void onRetryAttempt(String operation, int attempt, String sqlState) {
        client.emit(AppEvent.of("retry", source,
                attrs(operation, "attempt", attempt, "sqlState", nz(sqlState)), clock.getAsLong()));
    }

    @Override
    public void onRetryExhausted(String operation, int attempts, String sqlState) {
        client.emit(AppEvent.of("retry_exhausted", source,
                attrs(operation, "attempts", attempts, "sqlState", nz(sqlState)), clock.getAsLong()));
    }

    @Override
    public void onCircuitOpen(String operation, int consecutiveFailures) {
        client.emit(AppEvent.of("circuit_open", source,
                attrs(operation, "consecutiveFailures", consecutiveFailures, null, null), clock.getAsLong()));
    }

    private static Map<String, Object> attrs(String operation, String k1, Object v1, String k2, Object v2) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("operation", nz(operation));
        m.put(k1, v1);
        if (k2 != null) {
            m.put(k2, v2);
        }
        return m;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
