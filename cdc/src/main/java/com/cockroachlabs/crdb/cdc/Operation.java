package com.cockroachlabs.crdb.cdc;

import java.util.Map;

/**
 * The type of row change carried by a CDC event.
 */
public enum Operation {

    /** A new row was inserted (an {@code after} image with no {@code before}). */
    INSERT,

    /** An existing row was modified (both {@code before} and {@code after} present). */
    UPDATE,

    /** A row was deleted (an {@code after} image of {@code null}). */
    DELETE;

    /**
     * Infer the operation from a changefeed payload's before/after images.
     *
     * <p>CockroachDB always sends {@code after} for inserts/updates and a null
     * {@code after} for deletes. {@code before} is only present when the
     * changefeed was created {@code WITH diff}, so an update is only
     * distinguishable from an insert when diff is enabled; without diff, a row
     * that still exists is reported as {@link #INSERT}.
     */
    public static Operation fromImages(Map<String, Object> before, Map<String, Object> after) {
        if (after == null) {
            return DELETE;
        }
        if (before != null) {
            return UPDATE;
        }
        return INSERT;
    }
}
