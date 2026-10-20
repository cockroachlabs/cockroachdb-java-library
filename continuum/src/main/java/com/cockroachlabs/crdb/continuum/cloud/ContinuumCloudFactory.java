package com.cockroachlabs.crdb.continuum.cloud;

/**
 * Factory for {@link CloudClient}: returns a {@link SimulatedCloudClient} when the
 * client is disabled or has no API key, otherwise an {@link HttpCloudClient}.
 */
public final class ContinuumCloudFactory {

    private ContinuumCloudFactory() {
    }

    /** Creates a client using the default JDK transport. */
    public static CloudClient make(CloudClient.Config config) {
        return make(config, null);
    }

    /** Creates a client, overriding the HTTP transport (e.g. for tests). */
    public static CloudClient make(CloudClient.Config config, HttpCloudClient.Transport transport) {
        if (config == null || !config.enabled() || config.apiKey() == null || config.apiKey().isBlank()) {
            return new SimulatedCloudClient();
        }
        return new HttpCloudClient(config, transport);
    }
}
