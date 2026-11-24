package com.cockroachlabs.crdb.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Declares that the annotated entity's table should have the CockroachDB
 * {@code REGIONAL BY ROW} locality — each row is homed in a region (its
 * {@code crdb_region} value) so that region's nodes own the leaseholder and
 * reads/writes from there are fast. Per-user or per-tenant data is the usual
 * candidate.
 *
 * <p>This annotation is <strong>declared intent only</strong>: it does not
 * change the schema. Your migrations still own
 * {@code ALTER TABLE ... SET LOCALITY REGIONAL BY ROW}, and that statement
 * <em>rewrites the table</em>. Pair it with
 * {@link com.cockroachlabs.crdb.Crdb#verifyLocality} to check, at an explicit
 * point you choose, that the live table matches the annotation (and to get the
 * exact fix-up SQL when it does not).
 *
 * <p>Mark the field mapped to the region column with {@link CrdbRegion} so the
 * check knows which column homes the rows; or name the column directly with
 * {@link #regionColumn()}. With neither, CockroachDB's implicit
 * {@code crdb_region} column is assumed.
 *
 * <p>An entity must not carry both {@code @RegionalByRow} and
 * {@link GlobalTable}; that is a contradiction the check always reports.
 *
 * <p><strong>Table-name resolution</strong> follows the same rule and the same
 * one limitation as {@link GlobalTable#table()}: the simple class name folded to
 * lower case, no snake_case conversion; override with {@link #table()}.
 */
@Documented
@Retention(RUNTIME)
@Target(TYPE)
public @interface RegionalByRow {

    /**
     * The table this entity maps to, optionally schema-qualified and/or quoted.
     * Empty (the default) means the entity's simple class name, folded to lower
     * case. See {@link GlobalTable#table()} for the resolution rules.
     */
    String table() default "";

    /**
     * The name of the region column that homes each row. Empty (the default)
     * means: use the column of the {@link CrdbRegion}-annotated field if there
     * is one, otherwise CockroachDB's implicit {@code crdb_region}. Set this
     * when the region column is not represented by a field. Folded to lower case
     * like any unquoted identifier.
     */
    String regionColumn() default "";
}
