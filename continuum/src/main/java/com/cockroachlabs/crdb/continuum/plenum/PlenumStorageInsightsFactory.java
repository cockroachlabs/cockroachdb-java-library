package com.cockroachlabs.crdb.continuum.plenum;

import com.cockroachlabs.crdb.spi.StorageInsights;
import com.cockroachlabs.crdb.spi.StorageInsightsFactory;

import javax.sql.DataSource;

/**
 * {@link StorageInsightsFactory} provider registered via {@code META-INF/services}
 * so {@code Crdb.storage(ds)} resolves to {@link StorageInsightsImpl}.
 */
public final class PlenumStorageInsightsFactory implements StorageInsightsFactory {

    @Override
    public StorageInsights create(DataSource dataSource) {
        return new StorageInsightsImpl(dataSource);
    }
}
