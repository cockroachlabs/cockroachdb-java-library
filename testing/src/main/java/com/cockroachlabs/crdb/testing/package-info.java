/**
 * Testing utilities for applications built on the library.
 *
 * <p>{@link com.cockroachlabs.crdb.testing.CockroachHarness} provides a
 * Testcontainers-backed or external CockroachDB node for integration tests;
 * {@link com.cockroachlabs.crdb.testing.ConcurrencyRunner} drives a task on many
 * threads from a shared barrier to stress serialization behavior, with
 * {@link com.cockroachlabs.crdb.testing.SerializationConflicts} supplying a
 * ready-made conflict workload. {@link com.cockroachlabs.crdb.testing.RetrySimulator}
 * and {@link com.cockroachlabs.crdb.testing.SimulatedRetryException} exercise the
 * core retry path deterministically without a live database.
 */
package com.cockroachlabs.crdb.testing;
