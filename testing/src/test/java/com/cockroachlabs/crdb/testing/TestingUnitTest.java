package com.cockroachlabs.crdb.testing;

import com.cockroachlabs.crdb.retry.RetryEngine;
import com.cockroachlabs.crdb.retry.RetryPolicy;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestingUnitTest {

    @Test
    void retrySimulatorFailsThenSucceeds() throws Exception {
        RetrySimulator sim = new RetrySimulator(2);
        assertThrows(SimulatedRetryException.class, () -> sim.execute(() -> "x"));
        assertThrows(SimulatedRetryException.class, () -> sim.execute(() -> "x"));
        assertEquals("x", sim.execute(() -> "x"));
        assertEquals(3, sim.invocations());
    }

    @Test
    void simulatedRetryExceptionCarries40001() {
        assertEquals("40001", new SimulatedRetryException(0).getSQLState());
    }

    @Test
    void retrySimulatorDrivesCoreRetryEngine() {
        // No-op sleeper keeps the test instant; jitter source unused.
        RetryEngine engine = new RetryEngine(null, millis -> { });
        RetrySimulator sim = new RetrySimulator(2);

        String result = engine.run(RetryPolicy.defaults(), () -> sim.execute(() -> "done"));

        assertEquals("done", result);
        assertEquals(3, sim.invocations(), "2 simulated conflicts + 1 success");
    }

    @Test
    void concurrencyRunnerCountsSuccesses() {
        AtomicInteger counter = new AtomicInteger();
        ConcurrencyResult result = new ConcurrencyRunner(4, 25).run((w, i) -> counter.incrementAndGet());
        assertEquals(100, result.totalRuns());
        assertEquals(100, result.successes());
        assertEquals(0, result.failures());
        assertEquals(100, counter.get());
    }

    @Test
    void concurrencyRunnerCollectsFailures() {
        ConcurrencyResult result = new ConcurrencyRunner(2, 10).run((w, i) -> {
            if (w == 0) {
                throw new IllegalStateException("boom");
            }
        });
        assertEquals(20, result.totalRuns());
        assertEquals(10, result.failures());
        assertEquals(10, result.successes());
        assertEquals(0.5, result.failureRate(), 1e-9);
        assertTrue(result.errors().stream().allMatch(e -> e instanceof IllegalStateException));
    }

    @Test
    void externalHarnessExposesUrlAndDataSource() {
        String url = "jdbc:postgresql://localhost:26257/defaultdb?sslmode=disable";
        CockroachHarness h = CockroachHarness.external(url);
        assertEquals(url, h.jdbcUrl());
        assertNotNull(h.dataSource());
        h.close(); // no-op for external
    }

    @Test
    void containerHarnessRequiresStartBeforeUse() {
        // Construction must not require Docker; only start() does.
        CockroachHarness h = CockroachHarness.container();
        assertThrows(IllegalStateException.class, h::jdbcUrl);
    }
}
