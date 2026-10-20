package com.cockroachlabs.crdb.circuit;

import com.cockroachlabs.crdb.anomaly.CircuitOpen;
import com.cockroachlabs.crdb.anomaly.CrdbException;

import java.util.concurrent.Callable;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.LongSupplier;

/**
 * A thread-safe circuit breaker.
 *
 * <p>In {@link State#CLOSED} it counts consecutive failures; once the count
 * reaches the threshold it trips {@link State#OPEN} and rejects calls with
 * {@link CrdbException}({@link CircuitOpen}) until the reset timeout elapses,
 * after which a single {@link State#HALF_OPEN} trial probes recovery.
 */
public final class CircuitBreaker {

    private final int failureThreshold;
    private final long resetTimeoutMillis;
    private final LongSupplier clock;
    private final ReentrantLock lock = new ReentrantLock();

    private State state = State.CLOSED;
    private int consecutiveFailures;
    private long openedAtMillis;
    private boolean trialInProgress;

    public CircuitBreaker(int failureThreshold, long resetTimeoutMillis) {
        this(failureThreshold, resetTimeoutMillis, System::currentTimeMillis);
    }

    /**
     * @param clock supplies the current time in millis (inject for tests)
     */
    public CircuitBreaker(int failureThreshold, long resetTimeoutMillis, LongSupplier clock) {
        if (failureThreshold < 1) {
            throw new IllegalArgumentException("failureThreshold must be >= 1");
        }
        this.failureThreshold = failureThreshold;
        this.resetTimeoutMillis = resetTimeoutMillis;
        this.clock = clock;
    }

    /** Current breaker state (snapshot). */
    public State state() {
        lock.lock();
        try {
            return state;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Execute {@code work} through the breaker.
     *
     * @throws CrdbException with a {@link CircuitOpen} anomaly when the breaker
     *                       is open (call is not attempted)
     */
    public <T> T execute(Callable<T> work) {
        permit();
        boolean success = false;
        try {
            T result = work.call();
            success = true;
            return result;
        } catch (Exception e) {
            throw asRuntime(e);
        } finally {
            if (success) {
                onSuccess();
            } else {
                onFailure();
            }
        }
    }

    private void permit() {
        lock.lock();
        try {
            if (state == State.OPEN) {
                if (clock.getAsLong() - openedAtMillis >= resetTimeoutMillis) {
                    state = State.HALF_OPEN;
                    trialInProgress = true;
                    return;
                }
                throw new CrdbException(new CircuitOpen(consecutiveFailures));
            }
            if (state == State.HALF_OPEN) {
                if (trialInProgress) {
                    throw new CrdbException(new CircuitOpen(consecutiveFailures));
                }
                trialInProgress = true;
            }
        } finally {
            lock.unlock();
        }
    }

    private void onSuccess() {
        lock.lock();
        try {
            consecutiveFailures = 0;
            state = State.CLOSED;
            trialInProgress = false;
        } finally {
            lock.unlock();
        }
    }

    private void onFailure() {
        lock.lock();
        try {
            if (state == State.HALF_OPEN) {
                state = State.OPEN;
                openedAtMillis = clock.getAsLong();
                trialInProgress = false;
                com.cockroachlabs.crdb.observe.CrdbEvents.fireCircuitOpen("", consecutiveFailures);
                return;
            }
            consecutiveFailures++;
            if (consecutiveFailures >= failureThreshold) {
                state = State.OPEN;
                openedAtMillis = clock.getAsLong();
                com.cockroachlabs.crdb.observe.CrdbEvents.fireCircuitOpen("", consecutiveFailures);
            }
        } finally {
            lock.unlock();
        }
    }

    private static RuntimeException asRuntime(Exception ex) {
        if (ex instanceof RuntimeException re) {
            return re;
        }
        return new RuntimeException(ex);
    }
}
