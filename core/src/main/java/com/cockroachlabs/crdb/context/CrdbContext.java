package com.cockroachlabs.crdb.context;

import com.cockroachlabs.crdb.annotations.Workload.Priority;

import java.util.Optional;
import java.util.concurrent.Callable;

/**
 * Access point for the current {@link CrdbContextValues}, bound via
 * {@link java.lang.ScopedValue} so it is safe under virtual threads and
 * structured concurrency.
 */
public final class CrdbContext {

    private static final ScopedValue<CrdbContextValues> CTX = ScopedValue.newInstance();

    private CrdbContext() {
    }

    /**
     * Run {@code body} with {@code values} merged onto any currently-bound
     * context. Nested calls refine, they do not clobber, the parent scope.
     *
     * @return the value produced by {@code body}
     */
    public static <T> T with(CrdbContextValues values, Callable<T> body) {
        CrdbContextValues effective = current().map(c -> c.merge(values)).orElse(values);
        try {
            return ScopedValue.where(CTX, effective).call(body::call);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** The full context bound to the current scope, if any. */
    public static Optional<CrdbContextValues> current() {
        return CTX.isBound() ? Optional.of(CTX.get()) : Optional.empty();
    }

    /** The region bound to the current scope, if any. */
    public static Optional<String> region() {
        return current().map(CrdbContextValues::region).filter(v -> v != null);
    }

    /** The virtual cluster bound to the current scope, if any. */
    public static Optional<String> virtualCluster() {
        return current().map(CrdbContextValues::virtualCluster).filter(v -> v != null);
    }

    /** The workload priority bound to the current scope, if any. */
    public static Optional<Priority> workload() {
        return current().map(CrdbContextValues::workload).filter(v -> v != null);
    }

    /** The follower-read staleness expression bound to the current scope, if any. */
    public static Optional<String> followerReadStaleness() {
        return current().map(CrdbContextValues::followerReadStaleness).filter(v -> v != null);
    }
}
