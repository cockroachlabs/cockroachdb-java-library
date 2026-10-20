package com.cockroachlabs.crdb.continuum.mcp;

import com.cockroachlabs.crdb.continuum.aegis.McpAegisClient;
import com.cockroachlabs.crdb.continuum.mcp.HttpMcpClient.Response;
import com.cockroachlabs.crdb.continuum.mcp.McpClient.CallResult;
import com.cockroachlabs.crdb.continuum.mcp.McpClient.Config;
import com.cockroachlabs.crdb.continuum.mcp.McpClient.Content;
import com.cockroachlabs.crdb.continuum.mcp.McpClient.Tool;
import com.cockroachlabs.crdb.spi.AppEvent;
import com.cockroachlabs.crdb.spi.Finding;
import com.cockroachlabs.crdb.spi.Recommendation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class McpTest {

    /** Records requests and replies from a scripted queue. */
    private static final class FakeTransport implements HttpMcpClient.Transport {
        final List<String> bodies = new ArrayList<>();
        final List<Map<String, String>> headers = new ArrayList<>();
        private final List<Response> replies = new ArrayList<>();
        private int i = 0;

        FakeTransport reply(int status, Map<String, String> h, String body) {
            replies.add(new Response(status, h, body));
            return this;
        }

        @Override
        public Response send(String method, String url, Map<String, String> h, String body) {
            bodies.add(body);
            headers.add(h);
            return replies.get(i++);
        }
    }

    // ---- factory + simulated ----

    @Test
    void factorySelectsSimulatedWhenNoEndpoint() {
        assertTrue(ContinuumMcpFactory.create(new Config(false, "", "")) instanceof SimulatedMcpClient);
        assertTrue(ContinuumMcpFactory.create(new Config(true, "https://x/mcp", "t"), new FakeTransport())
                instanceof HttpMcpClient);
    }

    @Test
    void simulatedListsToolsAndEchoes() {
        SimulatedMcpClient c = new SimulatedMcpClient();
        assertEquals(2, c.listTools().size());
        assertEquals("pong", c.callTool("ping", Map.of()).text());
        assertTrue(c.callTool("echo", Map.of("k", "v")).text().contains("\"k\":\"v\""));
        assertEquals(2, c.calls().size());
    }

    // ---- HTTP client ----

    @Test
    void httpInitializeListEchoesSessionAndParses() {
        FakeTransport t = new FakeTransport()
                .reply(200, Map.of("Content-Type", "application/json", "Mcp-Session-Id", "sess-123"),
                        "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"protocolVersion\":\"2025-06-18\"}}")
                .reply(202, Map.of(), "")
                .reply(200, Map.of("Content-Type", "application/json"),
                        "{\"jsonrpc\":\"2.0\",\"id\":3,\"result\":{\"tools\":"
                                + "[{\"name\":\"list_findings\",\"description\":\"d\"}]}}");
        HttpMcpClient c = new HttpMcpClient(new Config(true, "https://x/mcp", "tok"), t);

        List<Tool> tools = c.listTools();
        assertEquals(1, tools.size());
        assertEquals("list_findings", tools.get(0).name());

        // initialize, notifications/initialized, tools/list.
        assertTrue(t.bodies.get(0).contains("\"initialize\""));
        assertTrue(t.bodies.get(1).contains("notifications/initialized"));
        assertTrue(t.bodies.get(2).contains("tools/list"));
        // every request carries the Bearer + both Accept types.
        for (Map<String, String> h : t.headers) {
            assertEquals("Bearer tok", h.get("Authorization"));
            assertTrue(h.get("Accept").contains("text/event-stream"));
        }
        // tools/list (index 2) echoes the server session id + protocol header.
        assertEquals("sess-123", t.headers.get(2).get("Mcp-Session-Id"));
        assertEquals("2025-06-18", t.headers.get(2).get("MCP-Protocol-Version"));
    }

    @Test
    void httpSseResponseParsed() {
        FakeTransport t = new FakeTransport()
                .reply(200, Map.of("Content-Type", "application/json"), "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{}}")
                .reply(202, Map.of(), "")
                .reply(200, Map.of("Content-Type", "text/event-stream"),
                        "event: message\ndata: {\"jsonrpc\":\"2.0\",\"id\":3,"
                                + "\"result\":{\"content\":[{\"type\":\"text\",\"text\":\"ok\"}]}}\n\n");
        HttpMcpClient c = new HttpMcpClient(new Config(true, "https://x/mcp", ""), t);
        assertEquals("ok", c.callTool("list_findings", Map.of()).text());
    }

    @Test
    void httpServerErrorSurfaced() {
        FakeTransport t = new FakeTransport()
                .reply(200, Map.of("Content-Type", "application/json"), "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{}}")
                .reply(202, Map.of(), "")
                .reply(200, Map.of("Content-Type", "application/json"),
                        "{\"jsonrpc\":\"2.0\",\"id\":3,\"error\":{\"code\":-32601,\"message\":\"method not found\"}}");
        HttpMcpClient c = new HttpMcpClient(new Config(true, "https://x/mcp", ""), t);
        RuntimeException ex = assertThrows(RuntimeException.class, c::listTools);
        assertTrue(ex.getMessage().contains("method not found"));
    }

    // ---- Aegis-over-MCP adapter ----

    /** Scripts CallResults by tool name and records calls. */
    private static final class FakeMcp implements McpClient {
        private final Map<String, CallResult> results;
        final List<Map.Entry<String, Map<String, Object>>> calls = new ArrayList<>();

        FakeMcp(Map<String, CallResult> results) {
            this.results = results;
        }

        @Override
        public List<Tool> listTools() {
            return List.of();
        }

        @Override
        public CallResult callTool(String name, Map<String, Object> arguments) {
            calls.add(Map.entry(name, arguments == null ? Map.of() : arguments));
            return results.getOrDefault(name, new CallResult(List.of(), false));
        }
    }

    private static CallResult text(String s) {
        return new CallResult(List.of(new Content("text", s)), false);
    }

    @Test
    void aegisFindingsAndRecommendationsParsed() {
        FakeMcp mcp = new FakeMcp(Map.of(
                "list_findings",
                text("[{\"id\":\"f1\",\"severity\":\"warning\",\"title\":\"hot\",\"detail\":\"d\"}]"),
                "list_recs",
                text("[{\"id\":\"r1\",\"title\":\"split\",\"detail\":\"d\",\"suggestedAction\":\"ALTER\"}]")));
        McpAegisClient c = new McpAegisClient(mcp,
                new McpAegisClient.ToolNames("list_findings", "list_recs", ""));

        List<Finding> fs = c.findings();
        assertEquals(1, fs.size());
        assertEquals("f1", fs.get(0).id());
        assertEquals("warning", fs.get(0).severity());

        List<Recommendation> rs = c.recommendations();
        assertEquals("ALTER", rs.get(0).suggestedAction());
    }

    @Test
    void aegisEmitSendsArgs() {
        FakeMcp mcp = new FakeMcp(Map.of());
        McpAegisClient c = new McpAegisClient(mcp, new McpAegisClient.ToolNames("", "", "emit_event"));
        c.emit(new AppEvent("retry", "svc", Map.of("attempt", 2), 99L));
        assertEquals("emit_event", mcp.calls.get(0).getKey());
        assertEquals("retry", mcp.calls.get(0).getValue().get("kind"));
        assertEquals(99L, mcp.calls.get(0).getValue().get("epochMillis"));
    }

    @Test
    void aegisUnconfiguredToolThrows() {
        McpAegisClient c = new McpAegisClient(new FakeMcp(Map.of()),
                new McpAegisClient.ToolNames("", "", ""));
        assertThrows(IllegalStateException.class, c::findings);
        assertThrows(IllegalStateException.class, c::recommendations);
        assertThrows(IllegalStateException.class, () -> c.emit(new AppEvent("x", "y", Map.of(), 0L)));
    }

    @Test
    void aegisToolErrorSurfaced() {
        FakeMcp mcp = new FakeMcp(Map.of(
                "list_findings", new CallResult(List.of(new Content("text", "boom")), true)));
        McpAegisClient c = new McpAegisClient(mcp, new McpAegisClient.ToolNames("list_findings", "", ""));
        assertThrows(IllegalStateException.class, c::findings);
    }
}
