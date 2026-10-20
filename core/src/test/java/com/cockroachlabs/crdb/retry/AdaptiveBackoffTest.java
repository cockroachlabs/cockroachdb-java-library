package com.cockroachlabs.crdb.retry;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdaptiveBackoffTest {

    // Zero swing: 0.5 maps to (0.5*2 - 1) == 0.
    private static final java.util.function.DoubleSupplier NO_JITTER = () -> 0.5;

    @Test
    void monotonicAndBoundedWithoutJitter() {
        RetryPolicy policy = new RetryPolicy(10, 100, 5000, 2.0, 0.2);
        AdaptiveBackoff bo = new AdaptiveBackoff(policy, NO_JITTER);

        long prev = -1;
        for (int attempt = 0; attempt < 10; attempt++) {
            long d = bo.nextDelayMillis(attempt);
            assertTrue(d >= 100, "delay >= base");
            assertTrue(d <= 5000, "delay <= max");
            assertTrue(d >= prev, "non-decreasing");
            prev = d;
        }
        assertEquals(100, bo.nextDelayMillis(0), "first delay == base with no contention/jitter");
    }

    @Test
    void highContentionLengthensDelay() {
        RetryPolicy policy = new RetryPolicy(5, 100, 5000, 2.0, 0.2);
        AdaptiveBackoff calm = new AdaptiveBackoff(policy, NO_JITTER);
        AdaptiveBackoff busy = new AdaptiveBackoff(policy, NO_JITTER);

        for (int i = 0; i < 8; i++) {
            busy.record(5); // consistently high retry counts
        }
        assertEquals(0.0, calm.contentionRatio());
        assertTrue(busy.contentionRatio() > 0.9);

        long calmDelay = calm.nextDelayMillis(2);
        long busyDelay = busy.nextDelayMillis(2);
        assertTrue(busyDelay >= calmDelay, "contention should not shorten the delay");
        assertTrue(busyDelay <= 5000, "still bounded by max");
    }
}
