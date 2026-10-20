package com.cockroachlabs.crdb.retry;

import com.cockroachlabs.crdb.anomaly.CrdbException;
import com.cockroachlabs.crdb.anomaly.RetryExhausted;
import com.cockroachlabs.crdb.anomaly.UnknownAnomaly;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RetryEngineTest {

    // No-op sleeper so tests do not actually wait.
    private RetryEngine engine() {
        return new RetryEngine(() -> 0.5, millis -> { });
    }

    private static SQLException serializationError() {
        return new SQLException("restart transaction: TransactionRetryError", "40001");
    }

    @Test
    void retriesThenThrowsRetryExhausted() {
        RetryPolicy policy = new RetryPolicy(3, 1, 5, 2.0, 0.0);
        AtomicInteger calls = new AtomicInteger();

        CrdbException ex = assertThrows(CrdbException.class, () ->
                engine().run(policy, () -> {
                    calls.incrementAndGet();
                    throw serializationError();
                }));

        assertEquals(4, calls.get(), "initial attempt + 3 retries");
        RetryExhausted re = assertInstanceOf(RetryExhausted.class, ex.anomaly());
        assertEquals(4, re.attempts());
    }

    @Test
    void succeedsAfterTransientConflicts() {
        RetryPolicy policy = new RetryPolicy(5, 1, 5, 2.0, 0.0);
        AtomicInteger calls = new AtomicInteger();

        String result = engine().run(policy, () -> {
            if (calls.incrementAndGet() <= 2) {
                throw serializationError();
            }
            return "ok";
        });

        assertEquals("ok", result);
        assertEquals(3, calls.get(), "2 failures then success");
    }

    @Test
    void nonRetryableFailsImmediately() {
        RetryPolicy policy = new RetryPolicy(5, 1, 5, 2.0, 0.0);
        AtomicInteger calls = new AtomicInteger();

        CrdbException ex = assertThrows(CrdbException.class, () ->
                engine().run(policy, () -> {
                    calls.incrementAndGet();
                    throw new SQLException("unique violation", "23505");
                }));

        assertEquals(1, calls.get(), "not retried");
        assertInstanceOf(UnknownAnomaly.class, ex.anomaly());
    }
}
