package com.cockroachlabs.crdb.spi;

import java.util.OptionalLong;

/**
 * Storage usage for a single table (Cockroach Plenum insight). Sizes are
 * optional because they are best-effort and may be unavailable on some cluster
 * versions.
 *
 * @param schema the table's schema (e.g. {@code public})
 * @param table  the table name
 * @param rows   estimated row count, if available
 * @param bytes  approximate on-disk size in bytes, if available
 */
public record TableStorage(String schema, String table, OptionalLong rows, OptionalLong bytes) {
}
