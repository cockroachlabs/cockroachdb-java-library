package com.cockroachlabs.crdb.circuit;

import com.cockroachlabs.crdb.anomaly.CircuitOpen;
import com.cockroachlabs.crdb.anomaly.CrdbException;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CircuitBreakerTest {

    @Test
    void opensAfterThresholdAndHalfOpensAfterReset() {
        AtomicLong now = new AtomicLong(0);
        CircuitBreaker breaker = new CircuitBreaker(3, 1000, now::get);

        // 3 failures trip the breaker.
        for (int i = 0; i < 3; i++) {
            assertThrows(RuntimeException.class, () ->
                    breaker.execute(() -> {
                        throw new RuntimeException("fail");
                    }));
        }
        assertEquals(State.OPEN, breaker.state());

        // While OPEN, calls are rejected without running.
        CrdbException rejected = assertThrows(CrdbException.class, () ->
                breaker.execute(() -> "should-not-run"));
        assertInstanceOf(CircuitOpen.class, rejected.anomaly());

        // After the reset timeout, a HALF_OPEN trial is allowed; success closes it.
        now.set(1001);
        String result = breaker.execute(() -> "recovered");
        assertEquals("recovered", result);
        assertEquals(State.CLOSED, breaker.state());
    }

    @Test
    void halfOpenFailureReopens() {
        AtomicLong now = new AtomicLong(0);
        CircuitBreaker breaker = new CircuitBreaker(1, 500, now::get);

        assertThrows(RuntimeException.class, () ->
                breaker.execute(() -> {
                    throw new RuntimeException("fail");
                }));
        assertEquals(State.OPEN, breaker.state());

        now.set(600); // eligible for HALF_OPEN trial
        assertThrows(RuntimeException.class, () ->
                breaker.execute(() -> {
                    throw new RuntimeException("still failing");
                }));
        assertEquals(State.OPEN, breaker.state(), "failed trial reopens the breaker");
    }
}
