package com.cockroachlabs.crdb.proxy;

import com.cockroachlabs.crdb.annotations.CircuitBreaker;
import com.cockroachlabs.crdb.annotations.CrdbRetry;
import com.cockroachlabs.crdb.annotations.FollowerRead;
import com.cockroachlabs.crdb.annotations.Region;
import com.cockroachlabs.crdb.annotations.Transactional;
import com.cockroachlabs.crdb.annotations.VirtualCluster;
import com.cockroachlabs.crdb.annotations.Workload;
import com.cockroachlabs.crdb.config.CrdbConfig;
import com.cockroachlabs.crdb.context.CrdbContext;
import com.cockroachlabs.crdb.context.CrdbContextValues;
import com.cockroachlabs.crdb.retry.RetryEngine;
import com.cockroachlabs.crdb.retry.RetryPolicy;

import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The behavior composed around each enhanced method call. Shared by both proxy
 * strategies (JDK {@link java.lang.reflect.Proxy} and ByteBuddy subclass).
 *
 * <p>Composition order (outermost first), per the library contract:
 * <pre>CircuitBreaker → Retry/@Transactional → context → original</pre>
 */
final class CrdbDispatcher {

    private final CrdbConfig config;
    private final RetryEngine retryEngine = new RetryEngine();
    private final ConcurrentHashMap<Method, com.cockroachlabs.crdb.circuit.CircuitBreaker> breakers =
            new ConcurrentHashMap<>();

    CrdbDispatcher(CrdbConfig config) {
        this.config = config;
    }

    /** Dispatch a call, layering the annotated behaviors around {@code original}. */
    Object dispatch(Method method, OriginalCall original) throws Exception {
        CrdbRetry crdbRetry = method.getAnnotation(CrdbRetry.class);
        Transactional transactional = method.getAnnotation(Transactional.class);
        CircuitBreaker circuit = method.getAnnotation(CircuitBreaker.class);
        CrdbContextValues ctx = contextOf(method);
        boolean hasContext = ctx != null;

        // Fast path: no CRDB annotations at all → pass through unchanged.
        if (crdbRetry == null && transactional == null && circuit == null && !hasContext) {
            return original.call();
        }

        // Innermost: bind call context (region/vCluster/workload/follower-read).
        java.util.concurrent.Callable<Object> withContext = hasContext
                ? () -> CrdbContext.with(ctx, original::call)
                : original::call;

        // Middle: retry (from @CrdbRetry or @Transactional(retry=true)).
        boolean doRetry = crdbRetry != null || (transactional != null && transactional.retry());
        RetryPolicy policy = retryPolicyFor(crdbRetry);
        java.util.concurrent.Callable<Object> withRetry = doRetry
                ? () -> retryEngine.run(policy, withContext)
                : withContext;

        // Outermost: circuit breaker.
        if (circuit != null) {
            return breakerFor(method, circuit).execute(withRetry);
        }
        return withRetry.call();
    }

    private RetryPolicy retryPolicyFor(CrdbRetry crdbRetry) {
        if (crdbRetry == null) {
            return config.retry();
        }
        RetryPolicy base = config.retry();
        return new RetryPolicy(
                crdbRetry.maxRetries(),
                crdbRetry.baseDelayMillis(),
                crdbRetry.maxDelayMillis(),
                base.multiplier(),
                base.jitter());
    }

    private com.cockroachlabs.crdb.circuit.CircuitBreaker breakerFor(Method method, CircuitBreaker cfg) {
        return breakers.computeIfAbsent(method,
                m -> new com.cockroachlabs.crdb.circuit.CircuitBreaker(
                        cfg.failureThreshold(), cfg.resetTimeoutMillis()));
    }

    private static CrdbContextValues contextOf(Method method) {
        Region region = method.getAnnotation(Region.class);
        VirtualCluster vc = method.getAnnotation(VirtualCluster.class);
        Workload workload = method.getAnnotation(Workload.class);
        FollowerRead follower = method.getAnnotation(FollowerRead.class);
        if (region == null && vc == null && workload == null && follower == null) {
            return null;
        }
        CrdbContextValues values = CrdbContextValues.empty();
        if (region != null) {
            values = values.withRegion(region.value());
        }
        if (vc != null) {
            values = values.withVirtualCluster(vc.value());
        }
        if (workload != null) {
            values = values.withWorkload(workload.value());
        }
        if (follower != null) {
            values = values.withFollowerReadStaleness(follower.staleness());
        }
        return values;
    }
}
