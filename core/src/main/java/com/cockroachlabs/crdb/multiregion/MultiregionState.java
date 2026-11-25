package com.cockroachlabs.crdb.multiregion;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.Nullable;

/**
 * What the multi-region check reads from CockroachDB.
 *
 * @param database the current database
 * @param currentSchema the schema that unqualified table names resolve to
 * @param primaryRegion the database's primary region, or {@code null} when it is not multi-region
 * @param clusterRegions the regions of the cluster's nodes, empty when no node has a region locality
 * @param tables the tables the check looks at that exist, by schema and name
 * @param keywords keywords that must be quoted when used as an identifier
 */
record MultiregionState(String database, String currentSchema, @Nullable String primaryRegion,
                        List<String> clusterRegions, Map<TableName, TableInfo> tables,
                        Set<String> keywords) {

    record TableName(String schema, String table) {
    }

    /**
     * @param locality as {@code SHOW TABLES} reports it, {@code null} outside a multi-region database
     * @param columnTypes the table's column types ({@code information_schema.columns.crdb_sql_type}),
     *                    read only for the columns the check needs
     */
    record TableInfo(@Nullable String locality, Map<String, String> columnTypes) {
    }
}
