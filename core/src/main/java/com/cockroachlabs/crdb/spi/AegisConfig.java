package com.cockroachlabs.crdb.spi;

/**
 * Configuration for the Cockroach Aegis client.
 *
 * @param enabled  whether Aegis integration is enabled
 * @param endpoint the Aegis API endpoint; {@code null}/blank selects the
 *                 simulated in-memory backend
 * @param token    bearer token for the Aegis API (may be {@code null})
 */
public record AegisConfig(boolean enabled, String endpoint, String token) {

    /** Disabled by default: selects the simulated backend, emits nothing remote. */
    public static AegisConfig defaults() {
        return new AegisConfig(false, null, null);
    }

    /** Whether the simulated backend should be used (disabled or no endpoint). */
    public boolean useSimulated() {
        return !enabled || endpoint == null || endpoint.isBlank();
    }
}
