package com.cockroachlabs.crdb.continuum.mcp;

import java.util.List;
import java.util.Map;

/**
 * Minimal client for the Model Context Protocol (MCP).
 *
 * <p>MCP is a public, versioned protocol, so (unlike the not-yet-public Aegis REST
 * API) this speaks the real wire format over the Streamable HTTP transport: the
 * initialize handshake, {@code tools/list}, and {@code tools/call}. It is used, for
 * example, to reach the Cockroach Continuum Cloud MCP server.
 *
 * <p>A {@link SimulatedMcpClient} is used when no endpoint is configured (see
 * {@link ContinuumMcpFactory}); otherwise an {@link HttpMcpClient}.
 */
public interface McpClient {

    /** Discover the tools the server advertises. */
    List<Tool> listTools();

    /** Invoke a tool by name with the given arguments. */
    CallResult callTool(String name, Map<String, Object> arguments);

    /** A tool advertised by an MCP server. */
    record Tool(String name, String title, String description) {
    }

    /** One content block of a tool-call result. */
    record Content(String type, String text) {
    }

    /** The result of a {@code tools/call}. */
    record CallResult(List<Content> content, boolean isError) {

        /** Concatenated text of all text content blocks. */
        public String text() {
            StringBuilder b = new StringBuilder();
            for (Content c : content) {
                if ("text".equals(c.type())) {
                    b.append(c.text());
                }
            }
            return b.toString();
        }
    }

    /** Configuration for an MCP client. A blank endpoint selects the simulated client. */
    record Config(boolean enabled, String endpoint, String token) {
        public Config {
            endpoint = endpoint == null ? "" : endpoint;
            token = token == null ? "" : token;
        }
    }
}
