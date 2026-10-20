package com.cockroachlabs.crdb.continuum.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * In-memory {@link McpClient} for development and tests. Advertises a small canned
 * tool set and echoes tool calls, so code that drives an MCP server runs offline.
 * Records every call for assertions via {@link #calls()}.
 */
public final class SimulatedMcpClient implements McpClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** A recorded tools/call. */
    public record Call(String name, Map<String, Object> arguments) {
    }

    private final List<Call> calls = new CopyOnWriteArrayList<>();

    @Override
    public List<Tool> listTools() {
        return List.of(
                new Tool("ping", "Ping", "Health check; returns pong."),
                new Tool("echo", "Echo", "Echoes its arguments as JSON text."));
    }

    @Override
    public CallResult callTool(String name, Map<String, Object> arguments) {
        Map<String, Object> args = arguments == null ? Map.of() : arguments;
        calls.add(new Call(name, args));
        String text;
        if ("ping".equals(name)) {
            text = "pong";
        } else {
            try {
                text = MAPPER.writeValueAsString(args);
            } catch (Exception e) {
                throw new RuntimeException("mcp: simulated marshal: " + e.getMessage(), e);
            }
        }
        return new CallResult(List.of(new Content("text", text)), false);
    }

    /** Recorded tool calls, in order. */
    public List<Call> calls() {
        return new ArrayList<>(calls);
    }
}
