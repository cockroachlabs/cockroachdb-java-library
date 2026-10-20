package com.cockroachlabs.crdb.continuum.cloud;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * HTTP-backed {@link CloudClient} using a service-account Bearer token.
 *
 * <p>HTTP I/O goes through a pluggable {@link Transport} (default: the JDK
 * {@link HttpClient}) so the client is trivially testable without a live server.
 * Responses are parsed by their documented (snake_case) field names.
 */
public final class HttpCloudClient implements CloudClient {

    /** Default CockroachDB Cloud API base URL. */
    public static final String DEFAULT_BASE_URL = "https://cockroachlabs.cloud/api/v1";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** A pluggable HTTP transport: (method, url, headers, body) -> response. */
    public interface Transport {
        Response send(String method, String url, Map<String, String> headers, String body);
    }

    /** An HTTP response: status code and (text) body. */
    public record Response(int status, String body) {
    }

    private final String base;
    private final String apiKey;
    private final Transport transport;

    public HttpCloudClient(Config config, Transport transport) {
        String b = config.baseUrl();
        this.base = (b == null || b.isBlank()) ? DEFAULT_BASE_URL : b.replaceAll("/+$", "");
        this.apiKey = config.apiKey();
        this.transport = transport != null ? transport : jdkTransport();
    }

    private Response doReq(String method, String path, Object body) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer " + apiKey);
        headers.put("Accept", "application/json");
        String payload = null;
        if (body != null) {
            headers.put("Content-Type", "application/json");
            try {
                payload = MAPPER.writeValueAsString(body);
            } catch (Exception e) {
                throw new RuntimeException("cloud: marshal request: " + e.getMessage(), e);
            }
        }
        Response r = transport.send(method, base + path, headers, payload);
        if (r.status() < 200 || r.status() >= 300) {
            String msg = r.body() == null ? "" : r.body().substring(0, Math.min(200, r.body().length()));
            throw new RuntimeException("cloud: " + method + " " + path + ": status " + r.status() + ": " + msg);
        }
        return r;
    }

    private static JsonNode parse(String body) {
        if (body == null || body.isBlank()) {
            return MAPPER.createObjectNode();
        }
        try {
            return MAPPER.readTree(body);
        } catch (Exception e) {
            throw new RuntimeException("cloud: parse response: " + e.getMessage(), e);
        }
    }

    @Override
    public List<Cluster> listClusters() {
        JsonNode root = parse(doReq("GET", "/clusters", null).body());
        List<Cluster> out = new ArrayList<>();
        JsonNode arr = root.get("clusters");
        if (arr != null && arr.isArray()) {
            for (JsonNode n : arr) {
                out.add(toCluster(n));
            }
        }
        return out;
    }

    @Override
    public Cluster getCluster(String clusterId) {
        return toCluster(parse(doReq("GET", "/clusters/" + clusterId, null).body()));
    }

    @Override
    public void configureMetricExport(String clusterId, MetricExportConfig config) {
        if (config == null || config.target() == null) {
            throw new IllegalArgumentException("cloud: metric export target is required");
        }
        doReq("POST", "/clusters/" + clusterId + "/metricexport/" + config.target().path(),
                config.settings() == null ? Map.of() : config.settings());
    }

    private static Cluster toCluster(JsonNode n) {
        List<String> regions = new ArrayList<>();
        JsonNode r = n.get("regions");
        if (r != null && r.isArray()) {
            for (JsonNode x : r) {
                regions.add(x.asText());
            }
        }
        return new Cluster(txt(n, "id"), txt(n, "name"), txt(n, "plan"), txt(n, "state"),
                txt(n, "cloud_provider"), regions);
    }

    private static String txt(JsonNode n, String key) {
        JsonNode v = n.get(key);
        return v == null || v.isNull() ? null : v.asText();
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
                return new Response(resp.statusCode(), resp.body());
            } catch (Exception e) {
                throw new RuntimeException("cloud: transport error: " + e.getMessage(), e);
            }
        };
    }
}
