package com.cockroachlabs.crdb.continuum.mcp;

/**
 * Factory for {@link McpClient}: returns a {@link SimulatedMcpClient} when the
 * client is disabled or has no endpoint, otherwise an {@link HttpMcpClient}.
 */
public final class ContinuumMcpFactory {

    private ContinuumMcpFactory() {
    }

    /** Creates a client using the default JDK transport. */
    public static McpClient create(McpClient.Config config) {
        return create(config, null);
    }

    /** Creates a client, overriding the HTTP transport (e.g. for tests). */
    public static McpClient create(McpClient.Config config, HttpMcpClient.Transport transport) {
        if (config == null || !config.enabled() || config.endpoint().isBlank()) {
            return new SimulatedMcpClient();
        }
        return new HttpMcpClient(config, transport);
    }
}
