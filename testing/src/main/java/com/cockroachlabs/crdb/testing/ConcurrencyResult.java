package com.cockroachlabs.crdb.testing;

import java.time.Duration;
import java.util.List;

/**
 * Aggregate outcome of a {@link ConcurrencyRunner#run} execution.
 *
 * @param totalRuns  total task invocations attempted (workers × iterations)
 * @param successes  invocations that completed without throwing
 * @param failures   invocations that threw
 * @param errors     the throwables collected from failing invocations
 * @param duration   wall-clock time from barrier release to completion
 */
public record ConcurrencyResult(
        int totalRuns,
        int successes,
        int failures,
        List<Throwable> errors,
        Duration duration) {

    /** The fraction of invocations that failed, in [0, 1]. */
    public double failureRate() {
        return totalRuns == 0 ? 0.0 : (double) failures / totalRuns;
    }
}
