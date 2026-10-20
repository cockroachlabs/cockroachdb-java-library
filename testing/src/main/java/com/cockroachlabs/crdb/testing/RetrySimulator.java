package com.cockroachlabs.crdb.testing;

import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Simulates CockroachDB serialization failures for a configurable number of
 * attempts, then succeeds. Throws {@link SimulatedRetryException} (SQLSTATE
 * {@code 40001}) for the first {@code failCount} calls, so it drives the core
 * {@code RetryEngine}/{@code TransactionTemplate} retry path deterministically
 * without a live database. Thread-safe.
 */
public final class RetrySimulator {

    private final int failCount;
    private final AtomicInteger currentCount = new AtomicInteger();

    /** Fail the first {@code failCount} invocations, then delegate. */
    public RetrySimulator(int failCount) {
        this.failCount = failCount;
    }

    /**
     * Return a {@link SimulatedRetryException} for the first {@code failCount}
     * invocations; thereafter invoke and return {@code work}'s result.
     */
    public <T> T execute(Callable<T> work) throws Exception {
        int count = currentCount.getAndIncrement();
        if (count < failCount) {
            throw new SimulatedRetryException(count);
        }
        return work.call();
    }

    /** Number of invocations so far. */
    public int invocations() {
        return currentCount.get();
    }

    /** Reset the counter so the simulator can be reused between test cases. */
    public void reset() {
        currentCount.set(0);
    }
}
