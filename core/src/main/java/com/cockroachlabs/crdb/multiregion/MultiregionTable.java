package com.cockroachlabs.crdb.multiregion;

/**
 * The table of an entity, and what its {@code @GlobalTable} or {@code @RegionalByRow}
 * annotation, if any, expects of it. Names are as CockroachDB stores them: unquoted names
 * folded to lower case, quoted names verbatim.
 *
 * @param entity the entity's fully qualified class name, for messages (so it can be found in an IDE)
 * @param schema the table's schema, or {@code null} for the connection's current schema
 * @param table the table's name
 * @param kind what the annotations expect; {@link Kind#NONE} for an entity with neither
 * @param regionColumn the column of the entity's {@code @CrdbRegion} field, or {@code null}
 */
record MultiregionTable(String entity, String schema, String table, Kind kind, String regionColumn) {

    enum Kind {
        NONE, GLOBAL, REGIONAL_BY_ROW, BOTH
    }
}
