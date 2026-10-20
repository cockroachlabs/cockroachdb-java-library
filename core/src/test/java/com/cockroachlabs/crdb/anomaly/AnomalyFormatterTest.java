package com.cockroachlabs.crdb.anomaly;

import org.junit.jupiter.api.Test;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Exercises the exhaustive pattern-matching anomaly formatter. */
class AnomalyFormatterTest {

    @Test
    void describesEveryAnomalyKind() {
        assertTrue(CrdbException.describe(
                new SerializationConflict("40001", "restart", null)).startsWith("SERIALIZATION_CONFLICT"));
        assertTrue(CrdbException.describe(
                new ConnectionAnomaly("08006", "reset", null)).startsWith("CONNECTION"));
        assertTrue(CrdbException.describe(
                new CircuitOpen(5)).startsWith("CIRCUIT_OPEN"));
        assertTrue(CrdbException.describe(
                new UnknownAnomaly("23505", "dup", null)).startsWith("UNKNOWN"));
        String re = CrdbException.describe(
                new RetryExhausted(4, new SerializationConflict("40001", "restart", null)));
        assertTrue(re.startsWith("RETRY_EXHAUSTED after 4"));
        assertTrue(re.contains("SERIALIZATION_CONFLICT"));
    }

    @Test
    void exceptionCarriesAnomalyAndCause() {
        SQLException root = new SQLException("boom", "40001");
        SerializationConflict sc = new SerializationConflict("40001", "boom", root);
        CrdbException ex = new CrdbException(sc);
        assertSame(sc, ex.anomaly());
        assertSame(root, ex.getCause());
        assertEquals("boom", ex.getMessage());
    }
}
