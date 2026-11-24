package com.cockroachlabs.crdb.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Declares that the annotated entity's table should have the CockroachDB
 * {@code GLOBAL} locality — optimized for low-latency reads from every region,
 * at the cost of slower writes. Reference/lookup tables (currencies, feature
 * flags, product catalogs) are the usual candidates.
 *
 * <p>This annotation is <strong>declared intent only</strong>: it does not
 * change the schema. Your migrations still own
 * {@code ALTER TABLE ... SET LOCALITY GLOBAL}. Pair it with
 * {@link com.cockroachlabs.crdb.Crdb#verifyLocality} to check, at an explicit
 * point you choose, that the live table actually matches the annotation (and
 * to get the exact fix-up SQL when it does not).
 *
 * <p>An entity must not carry both {@code @GlobalTable} and
 * {@link RegionalByRow}; that is a contradiction the check always reports.
 *
 * <p><strong>Table-name resolution.</strong> Because this is a low-level JDBC
 * library with no ORM metamodel, the table name defaults to the entity's
 * {@linkplain Class#getSimpleName() simple class name}, folded to lower case the
 * way CockroachDB folds an unquoted identifier (so {@code Currency} resolves to
 * {@code currency}). There is <em>no</em> camelCase-to-snake_case conversion: if
 * your table is {@code feature_flags} but your class is {@code FeatureFlag}, set
 * {@link #table()} explicitly. The value may be schema-qualified
 * ({@code "app.currencies"}) and may use double-quotes for names that need them
 * ({@code "\"Currency\""}).
 */
@Documented
@Retention(RUNTIME)
@Target(TYPE)
public @interface GlobalTable {

    /**
     * The table this entity maps to, optionally schema-qualified and/or quoted.
     * Empty (the default) means the entity's simple class name, folded to lower
     * case. See the type-level javadoc for the resolution rules and their one
     * limitation (no snake_case conversion).
     */
    String table() default "";
}
