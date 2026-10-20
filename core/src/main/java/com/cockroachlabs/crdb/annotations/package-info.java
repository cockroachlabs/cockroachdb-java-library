/**
 * Runtime annotations that drive CockroachDB-aware behavior through the
 * {@link com.cockroachlabs.crdb.proxy.CrdbProxyFactory dynamic-proxy engine}.
 *
 * <p>The headline idea of this library: annotate a method, wrap the target with
 * {@link com.cockroachlabs.crdb.Crdb#enhance(Object)}, and the boilerplate
 * {@code try/catch} around serialization conflicts, retries, and circuit
 * breaking simply disappears.
 */
package com.cockroachlabs.crdb.annotations;
