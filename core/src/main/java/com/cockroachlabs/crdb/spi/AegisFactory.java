package com.cockroachlabs.crdb.spi;

/**
 * SPI for creating {@link AegisClient} instances. Implemented by the
 * {@code continuum} module and discovered via {@link java.util.ServiceLoader}.
 */
public interface AegisFactory {

    /** Create an Aegis client for the given configuration. */
    AegisClient create(AegisConfig config);
}
