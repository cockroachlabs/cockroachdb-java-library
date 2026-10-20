/**
 * Service-provider interfaces and value types for the Cockroach Continuum
 * surfaces exposed through {@link com.cockroachlabs.crdb.Crdb}.
 *
 * <p>The interfaces and records live in {@code core} (dependency-free) while the
 * implementations live in the {@code continuum} module and are discovered at
 * runtime via {@link java.util.ServiceLoader}. This keeps {@code core} clean and
 * lets callers use {@code Crdb.storage(...)} / {@code Crdb.aegis(...)} without a
 * compile-time dependency on the implementation.
 */
package com.cockroachlabs.crdb.spi;
