package com.cockroachlabs.crdb.metrics;

import com.cockroachlabs.crdb.observe.CrdbEvents;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Callable;

/**
 * Small facade tying a {@link MetricsRecorder} to the core observability bus.
 *
 * <p>Typical use:
 * <pre>{@code
 * var metrics = CrdbMetrics.inMemory().register();
 * String out = metrics.timeTransaction(() -> template.execute(SERIALIZABLE, true, conn -> ...));
 * MetricsSnapshot snap = ((InMemoryMetricsRecorder) metrics.recorder()).snapshot();
 * }</pre>
 *
 * <p>{@link #register()} attaches a {@link CrdbMetricsListener} so retries and
 * circuit-opens are counted automatically. {@link #timeTransaction} additionally
 * records transaction count, success/failure, and duration.
 */
public final class CrdbMetrics {

    private final MetricsRecorder recorder;
    private final CrdbMetricsListener listener;
    private volatile boolean registered;

    public CrdbMetrics(MetricsRecorder recorder) {
        this.recorder = Objects.requireNonNull(recorder, "recorder");
        this.listener = new CrdbMetricsListener(recorder);
    }

    /** A facade backed by an {@link InMemoryMetricsRecorder}. */
    public static CrdbMetrics inMemory() {
        return new CrdbMetrics(new InMemoryMetricsRecorder());
    }

    /** The underlying recorder (e.g. cast to {@link InMemoryMetricsRecorder} for snapshots). */
    public MetricsRecorder recorder() {
        return recorder;
    }

    /** Attach to the core observe bus so retry/circuit events are counted. Idempotent. */
    public synchronized CrdbMetrics register() {
        if (!registered) {
            CrdbEvents.addListener(listener);
            registered = true;
        }
        return this;
    }

    /** Detach from the core observe bus. */
    public synchronized void unregister() {
        if (registered) {
            CrdbEvents.removeListener(listener);
            registered = false;
        }
    }

    /**
     * Time {@code work} as a single transaction, recording its duration and
     * success/failure. Retries occurring inside {@code work} are counted
     * independently via the observe bus (see {@link #register()}); the retry
     * count passed to {@link MetricsRecorder#recordTransaction} here is 0 because
     * this helper cannot observe the inner attempt count.
     *
     * @return the value produced by {@code work}
     */
    public <T> T timeTransaction(Callable<T> work) {
        long start = System.nanoTime();
        boolean success = false;
        try {
            T result = work.call();
            success = true;
            return result;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            recorder.recordTransaction(Duration.ofNanos(System.nanoTime() - start), 0, success);
        }
    }

    /** Record an already-completed transaction directly. */
    public void recordTransaction(Duration duration, int retries, boolean success) {
        recorder.recordTransaction(duration, retries, success);
    }
}
