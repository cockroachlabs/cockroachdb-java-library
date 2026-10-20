package com.cockroachlabs.crdb.anomaly;

import org.junit.jupiter.api.Test;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnomalyClassifierTest {

    @Test
    void serializationFailureIsSerializationConflict() {
        SQLException e = new SQLException("restart transaction", "40001");
        assertInstanceOf(SerializationConflict.class, AnomalyClassifier.classify(e));
        assertTrue(AnomalyClassifier.isRetryable(e));
    }

    @Test
    void deadlockIsSerializationConflict() {
        SQLException e = new SQLException("deadlock", "40P01");
        assertInstanceOf(SerializationConflict.class, AnomalyClassifier.classify(e));
        assertTrue(AnomalyClassifier.isRetryable(e));
    }

    @Test
    void connectionClassIsConnectionAnomaly() {
        SQLException e = new SQLException("connection reset", "08006");
        assertInstanceOf(ConnectionAnomaly.class, AnomalyClassifier.classify(e));
        assertTrue(AnomalyClassifier.isRetryable(e));
    }

    @Test
    void otherStateIsUnknownAndNotRetryable() {
        SQLException e = new SQLException("unique violation", "23505");
        assertInstanceOf(UnknownAnomaly.class, AnomalyClassifier.classify(e));
        assertFalse(AnomalyClassifier.isRetryable(e));
    }

    @Test
    void nullSqlStateIsUnknown() {
        SQLException e = new SQLException("no state");
        CrdbAnomaly a = AnomalyClassifier.classify(e);
        assertInstanceOf(UnknownAnomaly.class, a);
        assertEquals("no state", a.message());
    }
}
