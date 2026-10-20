package com.cockroachlabs.crdb.spi;

import java.util.List;

/**
 * Read-only Cockroach Plenum storage insights. Reachable via
 * {@link com.cockroachlabs.crdb.Crdb#storage(javax.sql.DataSource)}.
 */
public interface StorageInsights {

    /** Storage summary for every user table in the connection's current database. */
    List<TableStorage> summary();

    /** Storage summary for every user table in the named database. */
    List<TableStorage> summary(String database);
}
