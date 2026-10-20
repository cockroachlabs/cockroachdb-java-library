package com.cockroachlabs.crdb.continuum.aegis;

import com.cockroachlabs.crdb.continuum.mcp.McpClient;
import com.cockroachlabs.crdb.spi.AegisClient;
import com.cockroachlabs.crdb.spi.AppEvent;
import com.cockroachlabs.crdb.spi.Finding;
import com.cockroachlabs.crdb.spi.Recommendation;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Adapts an {@link McpClient} to the {@link AegisClient} SPI.
 *
 * <p>Aegis is reachable read-only over the Cockroach Continuum Cloud MCP server.
 * This adapter calls the configured tools and parses their text content as JSON
 * into {@link Finding}/{@link Recommendation}. Tool names are required config
 * (discover them with {@link McpClient#listTools()}); an unset name throws rather
 * than guessing the server's tool contract.
 */
public final class McpAegisClient implements AegisClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final McpClient mcp;
    private final ToolNames tools;

    public McpAegisClient(McpClient mcp, ToolNames tools) {
        this.mcp = mcp;
        this.tools = tools;
    }

    /**
     * Maps Aegis operations to the tool names an MCP server advertises. The
     * Continuum MCP tool contract is not pinned here; names differ by server.
     */
    public record ToolNames(String findings, String recommendations, String emit) {
        public ToolNames {
            findings = findings == null ? "" : findings;
            recommendations = recommendations == null ? "" : recommendations;
            emit = emit == null ? "" : emit;
        }
    }

    @Override
    public List<Finding> findings() {
        List<JsonNode> rows = readList(tools.findings(), "findings");
        List<Finding> out = new ArrayList<>();
        for (JsonNode r : rows) {
            out.add(new Finding(txt(r, "id"), txt(r, "severity"), txt(r, "title"), txt(r, "detail")));
        }
        return out;
    }

    @Override
    public List<Recommendation> recommendations() {
        List<JsonNode> rows = readList(tools.recommendations(), "recommendations");
        List<Recommendation> out = new ArrayList<>();
        for (JsonNode r : rows) {
            out.add(new Recommendation(
                    txt(r, "id"), txt(r, "title"), txt(r, "detail"), txt(r, "suggestedAction")));
        }
        return out;
    }

    @Override
    public void emit(AppEvent event) {
        if (tools.emit().isBlank()) {
            throw new IllegalStateException("aegis: MCP tool name not configured: emit");
        }
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("kind", event.kind());
        args.put("source", event.source());
        args.put("attrs", event.attrs());
        args.put("epochMillis", event.epochMillis());
        McpClient.CallResult res = mcp.callTool(tools.emit(), args);
        if (res.isError()) {
            throw new IllegalStateException("aegis: emit tool reported error: " + res.text());
        }
    }

    private List<JsonNode> readList(String tool, String what) {
        if (tool == null || tool.isBlank()) {
            throw new IllegalStateException("aegis: MCP tool name not configured: " + what);
        }
        McpClient.CallResult res = mcp.callTool(tool, Map.of());
        if (res.isError()) {
            throw new IllegalStateException("aegis: " + what + " tool reported error: " + res.text());
        }
        String text = res.text();
        if (text == null || text.isBlank()) {
            return List.of();
        }
        JsonNode root;
        try {
            root = MAPPER.readTree(text);
        } catch (Exception e) {
            throw new RuntimeException("aegis: decode " + what + ": " + e.getMessage(), e);
        }
        if (!root.isArray()) {
            throw new IllegalStateException("aegis: " + what + ": expected a JSON array");
        }
        List<JsonNode> out = new ArrayList<>();
        root.forEach(out::add);
        return out;
    }

    private static String txt(JsonNode n, String key) {
        JsonNode v = n.get(key);
        return v == null || v.isNull() ? "" : v.asText();
    }
}
