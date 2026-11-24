/**
 * Checks that your entities' declared multi-region intent — {@code @GlobalTable},
 * {@code @RegionalByRow}, {@code @CrdbRegion} — matches the live CockroachDB schema, and
 * prints the exact fix-up SQL when it does not.
 *
 * <p>The one public entry point is {@link com.cockroachlabs.crdb.Crdb#verifyLocality}; it
 * returns a {@link com.cockroachlabs.crdb.multiregion.MultiregionReport} and reacts per
 * {@link com.cockroachlabs.crdb.multiregion.Mode}. Everything else in this package is internal.
 */
package com.cockroachlabs.crdb.multiregion;
