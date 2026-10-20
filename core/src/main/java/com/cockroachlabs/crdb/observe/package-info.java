/**
 * Lightweight observability hooks. Core components ({@code RetryEngine},
 * {@code CircuitBreaker}) notify registered {@link com.cockroachlabs.crdb.observe.CrdbEventListener}s
 * of retries, exhaustion, and circuit trips, and keep process-wide counters.
 *
 * <p>This is the seam the Cockroach Aegis exhaust (in the {@code continuum}
 * module) attaches to, with no hard dependency when Aegis is disabled.
 */
package com.cockroachlabs.crdb.observe;
