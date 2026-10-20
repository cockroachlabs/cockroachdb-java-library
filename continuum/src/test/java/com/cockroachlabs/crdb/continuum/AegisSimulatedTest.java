package com.cockroachlabs.crdb.continuum;

import com.cockroachlabs.crdb.Crdb;
import com.cockroachlabs.crdb.continuum.aegis.ContinuumAegisFactory;
import com.cockroachlabs.crdb.continuum.aegis.HttpAegisClient;
import com.cockroachlabs.crdb.continuum.aegis.SimulatedAegisClient;
import com.cockroachlabs.crdb.spi.AegisClient;
import com.cockroachlabs.crdb.spi.AegisConfig;
import com.cockroachlabs.crdb.spi.AppEvent;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Unit tests for the simulated Cockroach Aegis client and factory (no network). */
class AegisSimulatedTest {

    @Test
    void facadeResolvesSimulatedForDefaults() {
        AegisClient client = Crdb.aegis(AegisConfig.defaults());
        assertInstanceOf(SimulatedAegisClient.class, client);
        assertEquals(2, client.findings().size());
        assertEquals(2, client.recommendations().size());
    }

    @Test
    void emitIsRecorded() {
        SimulatedAegisClient client = new SimulatedAegisClient();
        client.emit(AppEvent.of("test", "unit", Map.of("k", "v"), 123L));
        assertEquals(1, client.emittedEvents().size());
        assertEquals("test", client.emittedEvents().get(0).kind());
        assertEquals(123L, client.emittedEvents().get(0).epochMillis());
    }

    @Test
    void factorySelectsBackendByConfig() {
        ContinuumAegisFactory factory = new ContinuumAegisFactory();
        assertInstanceOf(SimulatedAegisClient.class, factory.create(AegisConfig.defaults()));

        AegisClient http = factory.create(new AegisConfig(true, "https://aegis.example/api", "tok"));
        assertInstanceOf(HttpAegisClient.class, http);
        assertThrows(UnsupportedOperationException.class, http::findings);
    }
}
