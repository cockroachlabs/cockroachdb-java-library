package com.cockroachlabs.crdb.tx;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * A unit of work executed against an open, in-transaction {@link Connection}.
 * The connection's lifecycle (commit/rollback/close) is managed by
 * {@link TransactionTemplate}; the callback only issues statements.
 */
@FunctionalInterface
public interface ConnectionCallback<T> {

    /** Do work on the connection and return a result. */
    T run(Connection c) throws SQLException;
}
