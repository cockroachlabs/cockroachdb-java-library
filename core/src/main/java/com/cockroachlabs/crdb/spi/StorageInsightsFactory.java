package com.cockroachlabs.crdb.spi;

import javax.sql.DataSource;

/**
 * SPI for creating {@link StorageInsights} instances. Implemented by the
 * {@code continuum} module and discovered via {@link java.util.ServiceLoader}.
 */
public interface StorageInsightsFactory {

    /** Create a storage-insights view over {@code dataSource}. */
    StorageInsights create(DataSource dataSource);
}
