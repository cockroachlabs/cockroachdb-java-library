package com.cockroachlabs.crdb.observe;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Process-wide event bus and counters for CockroachDB operational signals.
 *
 * <p>Firing is cheap and lock-free; with no listeners registered it only bumps
 * atomic counters. Listeners are notified in registration order. Exceptions
 * thrown by a listener are swallowed so observability never breaks the app.
 */
public final class CrdbEvents {

    private static final List<CrdbEventListener> LISTENERS = new CopyOnWriteArrayList<>();
    private static final AtomicLong RETRY_ATTEMPTS = new AtomicLong();
    private static final AtomicLong RETRIES_EXHAUSTED = new AtomicLong();
    private static final AtomicLong CIRCUIT_OPENS = new AtomicLong();

    private CrdbEvents() {
    }

    public static void addListener(CrdbEventListener listener) {
        LISTENERS.add(listener);
    }

    public static void removeListener(CrdbEventListener listener) {
        LISTENERS.remove(listener);
    }

    /** Remove all listeners (does not reset counters). Intended for tests. */
    public static void clearListeners() {
        LISTENERS.clear();
    }

    public static void fireRetryAttempt(String operation, int attempt, String sqlState) {
        RETRY_ATTEMPTS.incrementAndGet();
        for (CrdbEventListener l : LISTENERS) {
            safe(() -> l.onRetryAttempt(operation, attempt, sqlState));
        }
    }

    public static void fireRetryExhausted(String operation, int attempts, String sqlState) {
        RETRIES_EXHAUSTED.incrementAndGet();
        for (CrdbEventListener l : LISTENERS) {
            safe(() -> l.onRetryExhausted(operation, attempts, sqlState));
        }
    }

    public static void fireCircuitOpen(String operation, int consecutiveFailures) {
        CIRCUIT_OPENS.incrementAndGet();
        for (CrdbEventListener l : LISTENERS) {
            safe(() -> l.onCircuitOpen(operation, consecutiveFailures));
        }
    }

    /** A snapshot of the current counters. */
    public static CrdbStats stats() {
        return new CrdbStats(RETRY_ATTEMPTS.get(), RETRIES_EXHAUSTED.get(), CIRCUIT_OPENS.get());
    }

    /** Reset all counters to zero. Intended for tests. */
    public static void resetStats() {
        RETRY_ATTEMPTS.set(0);
        RETRIES_EXHAUSTED.set(0);
        CIRCUIT_OPENS.set(0);
    }

    private static void safe(Runnable r) {
        try {
            r.run();
        } catch (RuntimeException ignored) {
            // Observability must never break the application path.
        }
    }
}
