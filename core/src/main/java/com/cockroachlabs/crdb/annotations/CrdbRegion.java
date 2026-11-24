package com.cockroachlabs.crdb.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Marks the field that maps to the {@code crdb_region} column of a
 * {@link RegionalByRow} entity — the column that homes each row in a region.
 *
 * <p>The column name defaults to the field's name, folded to lower case the way
 * CockroachDB folds an unquoted identifier (so a field {@code region} maps to
 * the column {@code region}). If the actual column name differs, name it with
 * {@link RegionalByRow#regionColumn()} on the type instead.
 *
 * <p>Used by {@link com.cockroachlabs.crdb.Crdb#verifyLocality}: when a
 * {@code @RegionalByRow} table is regional by a specific column, the check
 * confirms that column exists, is of type {@code crdb_internal_region}, and is
 * the one this field names.
 *
 * <p>This is distinct from the method-level {@link Region @Region}, which pins a
 * single call's execution to a region at runtime; {@code @CrdbRegion} is a
 * static, structural declaration about an entity's region column.
 */
@Documented
@Retention(RUNTIME)
@Target(FIELD)
public @interface CrdbRegion {
}
