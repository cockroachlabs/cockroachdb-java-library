package com.cockroachlabs.crdb.continuum;

import com.cockroachlabs.crdb.continuum.aegis.AegisExhaust;
import com.cockroachlabs.crdb.continuum.aegis.SimulatedAegisClient;
import com.cockroachlabs.crdb.observe.CrdbEvents;
import com.cockroachlabs.crdb.retry.RetryEngine;
import com.cockroachlabs.crdb.retry.RetryPolicy;
import com.cockroachlabs.crdb.spi.AppEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Aegis exhaust observes the core retry engine and emits app events. */
class AegisExhaustTest {

    @AfterEach
    void cleanup() {
        CrdbEvents.clearListeners();
        CrdbEvents.resetStats();
    }

    @Test
    void exhaustCapturesRetryEvents() {
        SimulatedAegisClient sim = new SimulatedAegisClient();
        try (AegisExhaust exhaust = new AegisExhaust(sim, () -> 42L, "test").register()) {
            RetryEngine engine = new RetryEngine(() -> 0.5, millis -> { }); // no real sleep
            AtomicInteger calls = new AtomicInteger();

            String result = engine.run(new RetryPolicy(5, 1, 5, 2.0, 0.0), () -> {
                if (calls.incrementAndGet() <= 2) {
                    throw new SQLException("restart transaction", "40001");
                }
                return "ok";
            });

            assertEquals("ok", result);
        }

        List<AppEvent> events = sim.emittedEvents();
        assertTrue(events.size() >= 2, "one exhaust event per retry attempt");
        assertTrue(events.stream().allMatch(e -> e.kind().equals("retry")));
        assertEquals(42L, events.get(0).epochMillis(), "injected clock used");
        assertEquals("40001", events.get(0).attrs().get("sqlState"));
    }
}
