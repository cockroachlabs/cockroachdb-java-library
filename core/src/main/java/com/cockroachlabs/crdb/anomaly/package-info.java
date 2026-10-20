/**
 * Typed, exhaustive modelling of CockroachDB failure modes.
 *
 * <p>{@link com.cockroachlabs.crdb.anomaly.CrdbAnomaly} is a sealed hierarchy so
 * callers can handle every failure kind with a pattern-matching {@code switch}
 * and let the compiler prove exhaustiveness.
 */
package com.cockroachlabs.crdb.anomaly;
