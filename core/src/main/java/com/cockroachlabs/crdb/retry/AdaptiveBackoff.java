package com.cockroachlabs.crdb.retry;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.DoubleSupplier;

/**
 * Computes backoff delays that <em>adapt</em> to observed contention.
 *
 * <p>The engine keeps a short rolling window of how many retries recent
 * operations actually needed. When contention is high the base delay is
 * lengthened (up to {@link RetryPolicy#maxDelayMillis()}); when the cluster is
 * calm it shrinks back toward {@link RetryPolicy#baseDelayMillis()}. Jitter is
 * drawn from an injectable {@link DoubleSupplier}, so the class is fully
 * deterministic under test.
 *
 * <p>Not thread-safe on its own; the retry engine synchronizes access.
 */
public final class AdaptiveBackoff {

    private static final int WINDOW = 16;

    private final RetryPolicy policy;
    private final DoubleSupplier jitterSource;

    private final int[] window = new int[WINDOW];
    private int count;
    private int cursor;
    private long sum;

    /** Uses {@link ThreadLocalRandom} for jitter. */
    public AdaptiveBackoff(RetryPolicy policy) {
        this(policy, () -> ThreadLocalRandom.current().nextDouble());
    }

    /**
     * @param policy       the retry policy driving base/max/multiplier/jitter
     * @param jitterSource supplies values in [0, 1); inject for deterministic tests
     */
    public AdaptiveBackoff(RetryPolicy policy, DoubleSupplier jitterSource) {
        this.policy = policy;
        this.jitterSource = jitterSource;
    }

    /**
     * Feed the observed number of retries an operation needed. Drives the
     * adaptive contention estimate.
     */
    public synchronized void record(int retriesUsed) {
        int slot = cursor % WINDOW;
        if (count == WINDOW) {
            sum -= window[slot];
        } else {
            count++;
        }
        window[slot] = retriesUsed;
        sum += retriesUsed;
        cursor++;
    }

    /**
     * Contention estimate in [0, 1]: recent average retries relative to the
     * policy's {@code maxRetries}.
     */
    public synchronized double contentionRatio() {
        if (count == 0 || policy.maxRetries() == 0) {
            return 0.0;
        }
        double avg = (double) sum / count;
        double ratio = avg / policy.maxRetries();
        return Math.max(0.0, Math.min(1.0, ratio));
    }

    /**
     * The delay to wait before the given zero-based {@code attempt}.
     * Guaranteed to lie within {@code [0, maxDelayMillis]}, and (with zero
     * jitter) to be non-decreasing in {@code attempt}.
     */
    public long nextDelayMillis(int attempt) {
        double raw = policy.baseDelayMillis() * Math.pow(policy.multiplier(), attempt);
        // Adaptive factor in [1, 2]: high contention lengthens the delay.
        double factor = 1.0 + contentionRatio();
        double scaled = raw * factor;

        // Clamp the deterministic component to [base, max] first so it stays
        // monotonic and bounded.
        double clamped = Math.max(policy.baseDelayMillis(),
                Math.min(scaled, policy.maxDelayMillis()));

        // Symmetric jitter: +/- (jitter * clamped).
        double j = policy.jitter();
        if (j > 0.0) {
            double swing = (jitterSource.getAsDouble() * 2.0 - 1.0) * j * clamped;
            clamped += swing;
        }
        long result = Math.round(clamped);
        // Final safety clamp into [0, max].
        if (result < 0) {
            result = 0;
        }
        return Math.min(result, policy.maxDelayMillis());
    }
}
