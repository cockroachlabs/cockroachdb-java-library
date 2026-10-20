package com.cockroachlabs.crdb.testing;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Spawns multiple threads that execute a task concurrently, releasing them from a
 * common barrier to maximize contention. Ideal for exercising CockroachDB's
 * serialization-conflict handling and the library's retry machinery under load.
 *
 * <pre>{@code
 * var runner = new ConcurrencyRunner(8, 50);
 * ConcurrencyResult result = runner.run((workerId, iteration) ->
 *         template.execute(SERIALIZABLE, true, conn -> increment(conn, "k")));
 * }</pre>
 */
public final class ConcurrencyRunner {

    /** A unit of concurrent work identified by worker and iteration index. */
    @FunctionalInterface
    public interface ConcurrentTask {
        void run(int workerId, int iteration) throws Exception;
    }

    private final int workers;
    private final int iterations;

    public ConcurrencyRunner(int workers, int iterations) {
        this.workers = workers;
        this.iterations = iterations;
    }

    /**
     * Run {@code task} on {@code workers} threads, each performing {@code iterations}
     * invocations. All threads wait at a barrier before starting.
     */
    public ConcurrencyResult run(ConcurrentTask task) {
        if (workers <= 0 || iterations <= 0) {
            return new ConcurrencyResult(0, 0, 0, List.of(), Duration.ZERO);
        }

        AtomicInteger successes = new AtomicInteger();
        AtomicInteger failures = new AtomicInteger();
        List<Throwable> errors = new CopyOnWriteArrayList<>();
        CyclicBarrier barrier = new CyclicBarrier(workers);
        List<Thread> threads = new java.util.ArrayList<>(workers);

        for (int w = 0; w < workers; w++) {
            final int workerId = w;
            Thread t = new Thread(() -> {
                try {
                    barrier.await();
                } catch (Exception e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                for (int i = 0; i < iterations; i++) {
                    try {
                        task.run(workerId, i);
                        successes.incrementAndGet();
                    } catch (Throwable e) {
                        failures.incrementAndGet();
                        errors.add(e);
                    }
                }
            }, "crdb-conc-" + w);
            threads.add(t);
        }

        long start = System.nanoTime();
        threads.forEach(Thread::start);
        for (Thread t : threads) {
            try {
                t.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        Duration duration = Duration.ofNanos(System.nanoTime() - start);

        int s = successes.get();
        int f = failures.get();
        return new ConcurrencyResult(s + f, s, f, List.copyOf(errors), duration);
    }
}
