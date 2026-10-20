/**
 * The dynamic-proxy engine: the headline feature that makes CockroachDB
 * anomaly {@code try/catch} disappear.
 *
 * <p>{@link com.cockroachlabs.crdb.proxy.CrdbProxyFactory} reads the annotations
 * on a target's methods and returns an enhanced instance that transparently
 * layers circuit breaking, adaptive retry, transactional semantics, and call
 * context around the original method — using a JDK {@link java.lang.reflect.Proxy}
 * when the target implements an interface, or a ByteBuddy subclass otherwise.
 */
package com.cockroachlabs.crdb.proxy;
