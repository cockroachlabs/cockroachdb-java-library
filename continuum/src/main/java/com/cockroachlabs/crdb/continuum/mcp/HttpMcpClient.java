package com.cockroachlabs.crdb.continuum.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * HTTP-backed {@link McpClient} speaking the MCP Streamable HTTP transport (spec
 * 2025-06-18). Lazily performs the initialize handshake before the first tools
 * call, echoes the server-assigned {@code Mcp-Session-Id} and the
 * {@code MCP-Protocol-Version} header, and handles both {@code application/json} and
 * {@code text/event-stream} responses.
 *
 * <p>HTTP I/O goes through a pluggable {@link Transport} (default: the JDK
 * {@link HttpClient}) so the client is testable without a live server. Responses
 * are parsed with Jackson.
 */
public final class HttpMcpClient implements McpClient {

    /** The MCP revision this client negotiates. */
    public static final String PROTOCOL_VERSION = "2025-06-18";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** A pluggable HTTP transport: (method, url, headers, body) -> response. */
    public interface Transport {
        Response send(String method, String url, Map<String, String> headers, String body);
    }

    /** An HTTP response: status code, headers, and (text) body. */
    public record Response(int status, Map<String, String> headers, String body) {
    }

    private final String endpoint;
    private final String token;
    private final String clientName;
    private final String clientVersion;
    private final Transport transport;
    private final AtomicLong ids = new AtomicLong();
    private String sessionId;
    private boolean init;

    public HttpMcpClient(Config config, Transport transport) {
        this(config, transport, "cockroachdb-java-library", "0.1");
    }

    public HttpMcpClient(Config config, Transport transport, String clientName, String clientVersion) {
        this.endpoint = config.endpoint();
        this.token = config.token();
        this.clientName = clientName;
        this.clientVersion = clientVersion;
        this.transport = transport != null ? transport : jdkTransport();
    }

    @Override
    public List<Tool> listTools() {
        ensureInit();
        JsonNode result = call("tools/list", Map.of());
        List<Tool> out = new ArrayList<>();
        JsonNode arr = result.get("tools");
        if (arr != null && arr.isArray()) {
            for (JsonNode n : arr) {
                out.add(new Tool(txt(n, "name"), txt(n, "title"), txt(n, "description")));
            }
        }
        return out;
    }

    @Override
    public CallResult callTool(String name, Map<String, Object> arguments) {
        ensureInit();
        JsonNode result = call("tools/call",
                Map.of("name", name, "arguments", arguments == null ? Map.of() : arguments));
        List<Content> content = new ArrayList<>();
        JsonNode arr = result.get("content");
        if (arr != null && arr.isArray()) {
            for (JsonNode n : arr) {
                content.add(new Content(txt(n, "type"), txt(n, "text")));
            }
        }
        JsonNode isErr = result.get("isError");
        return new CallResult(content, isErr != null && isErr.asBoolean(false));
    }

    private void ensureInit() {
        if (init) {
            return;
        }
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("protocolVersion", PROTOCOL_VERSION);
        params.put("capabilities", Map.of());
        params.put("clientInfo", Map.of("name", clientName, "version", clientVersion));
        call("initialize", params);
        try {
            notifyServer("notifications/initialized");
        } catch (RuntimeException ignored) {
            // A rejected notification must not block us.
        }
        init = true;
    }

    private JsonNode call(String method, Map<String, Object> params) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("jsonrpc", "2.0");
        payload.put("id", ids.incrementAndGet());
        payload.put("method", method);
        payload.put("params", params);

        String text = post(payload);
        if (text == null || text.isBlank()) {
            throw new RuntimeException("mcp: " + method + ": unexpected empty response");
        }
        JsonNode env = parse(text);
        JsonNode err = env.get("error");
        if (err != null && err.isObject()) {
            throw new RuntimeException("mcp: " + method + ": server error "
                    + txt(err, "code") + ": " + txt(err, "message"));
        }
        JsonNode result = env.get("result");
        return result == null ? MAPPER.createObjectNode() : result;
    }

    private void notifyServer(String method) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("jsonrpc", "2.0");
        payload.put("method", method);
        post(payload);
    }

    private String post(Map<String, Object> payload) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Accept", "application/json, text/event-stream");
        if (token != null && !token.isBlank()) {
            headers.put("Authorization", "Bearer " + token);
        }
        if (sessionId != null) {
            headers.put("Mcp-Session-Id", sessionId);
        }
        if (init) {
            headers.put("MCP-Protocol-Version", PROTOCOL_VERSION);
        }

        String body;
        try {
            body = MAPPER.writeValueAsString(payload);
        } catch (Exception e) {
            throw new RuntimeException("mcp: marshal request: " + e.getMessage(), e);
        }
        Response r = transport.send("POST", endpoint, headers, body);

        String sid = headerValue(r.headers(), "Mcp-Session-Id");
        if (sid != null && !sid.isBlank()) {
            sessionId = sid;
        }
        if (r.status() == 202) {
            return null;
        }
        if (r.status() < 200 || r.status() >= 300) {
            String msg = r.body() == null ? "" : r.body().substring(0, Math.min(200, r.body().length()));
            throw new RuntimeException("mcp: HTTP " + r.status() + ": " + msg);
        }
        String ct = headerValue(r.headers(), "Content-Type");
        if (ct != null && ct.startsWith("text/event-stream")) {
            return extractSse(r.body());
        }
        return r.body();
    }

    private static JsonNode parse(String body) {
        try {
            return MAPPER.readTree(body);
        } catch (Exception e) {
            throw new RuntimeException("mcp: parse response: " + e.getMessage(), e);
        }
    }

    private static String extractSse(String body) {
        if (body == null) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        for (String line : body.split("\n")) {
            String l = line.strip();
            if (l.startsWith("data:")) {
                out.append(l.substring(5).strip());
            }
        }
        return out.toString();
    }

    private static String headerValue(Map<String, String> headers, String name) {
        for (Map.Entry<String, String> e : headers.entrySet()) {
            if (e.getKey().equalsIgnoreCase(name)) {
                return e.getValue();
            }
        }
        return null;
    }

    private static String txt(JsonNode n, String key) {
        JsonNode v = n.get(key);
        return v == null || v.isNull() ? "" : v.asText();
    }

    /** The default transport backed by the JDK {@link HttpClient}. */
    public static Transport jdkTransport() {
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        return (method, url, headers, body) -> {
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url));
            HttpRequest.BodyPublisher pub = body == null
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(body);
            b.method(method, pub);
            headers.forEach(b::header);
            try {
                HttpResponse<String> resp = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
                Map<String, String> respHeaders = new HashMap<>();
                resp.headers().map().forEach((k, v) -> {
                    if (!v.isEmpty()) {
                        respHeaders.put(k, v.get(0));
                    }
                });
                return new Response(resp.statusCode(), respHeaders, resp.body());
            } catch (Exception e) {
                throw new RuntimeException("mcp: transport error: " + e.getMessage(), e);
            }
        };
    }
}
