package com.cockroachlabs.crdb.continuum.aegis;

import com.cockroachlabs.crdb.spi.AegisClient;
import com.cockroachlabs.crdb.spi.AegisConfig;
import com.cockroachlabs.crdb.spi.AegisFactory;

/**
 * {@link AegisFactory} provider registered via {@code META-INF/services}.
 * Returns the {@link SimulatedAegisClient} when the config is disabled or has no
 * endpoint, otherwise the {@link HttpAegisClient}.
 */
public final class ContinuumAegisFactory implements AegisFactory {

    @Override
    public AegisClient create(AegisConfig config) {
        AegisConfig cfg = config == null ? AegisConfig.defaults() : config;
        return cfg.useSimulated() ? new SimulatedAegisClient() : new HttpAegisClient(cfg);
    }
}
