package com.cockroachlabs.crdb.cdc;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Receives CockroachDB changefeed webhook POSTs, parses the envelope, and
 * dispatches decoded events to a {@link ChangefeedHandlerRegistry}. Resolved
 * timestamps are delivered to an optional callback instead of the registry.
 *
 * <p>Implements {@link HttpHandler} so it can be mounted directly on a JDK
 * {@code HttpServer}:
 * <pre>{@code
 * var handler = new ChangefeedWebhookHandler(registry).onResolved(ts -> checkpoint(ts));
 * var server = HttpServer.create(new InetSocketAddress(8443), 0);
 * server.createContext("/cdc", handler);
 * server.start();
 * }</pre>
 *
 * <p>The core parsing/dispatch logic is also exposed as {@link #handleBody(String)}
 * so it can be unit-tested without an HTTP server.
 */
public final class ChangefeedWebhookHandler implements HttpHandler {

    /** Default maximum request body size: 10 MiB. */
    public static final long DEFAULT_MAX_BODY_BYTES = 10L * 1024 * 1024;

    private final ChangefeedHandlerRegistry registry;
    private final CdcEventDecoder decoder;
    private long maxBodyBytes = DEFAULT_MAX_BODY_BYTES;
    private Consumer<String> onResolved;

    public ChangefeedWebhookHandler(ChangefeedHandlerRegistry registry) {
        this(registry, new CdcEventDecoder());
    }

    public ChangefeedWebhookHandler(ChangefeedHandlerRegistry registry, CdcEventDecoder decoder) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.decoder = Objects.requireNonNull(decoder, "decoder");
    }

    /** Set the maximum accepted request body size in bytes. */
    public ChangefeedWebhookHandler maxBodyBytes(long bytes) {
        this.maxBodyBytes = bytes;
        return this;
    }

    /** Register a callback for resolved-timestamp checkpoints. */
    public ChangefeedWebhookHandler onResolved(Consumer<String> callback) {
        this.onResolved = callback;
        return this;
    }

    /**
     * Parse a raw webhook body and dispatch it. A resolved-timestamp envelope
     * invokes the {@link #onResolved(Consumer)} callback (if set) and returns 0;
     * a data envelope is decoded and dispatched via the registry.
     *
     * @return the number of tables dispatched (0 for a resolved checkpoint)
     */
    public int handleBody(String body) {
        CdcEnvelope envelope = decoder.decode(body);
        if (envelope.isResolved()) {
            if (onResolved != null) {
                onResolved.accept(envelope.resolved());
            }
            return 0;
        }
        List<CdcEvent> events = decoder.toEvents(envelope);
        return registry.dispatch(events);
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            String body = readBody(exchange);
            if (body == null) {
                exchange.sendResponseHeaders(413, -1); // payload too large
                return;
            }
            try {
                handleBody(body);
                exchange.sendResponseHeaders(200, -1);
            } catch (RuntimeException e) {
                exchange.sendResponseHeaders(400, -1); // malformed / handler error
            }
        }
    }

    private String readBody(HttpExchange exchange) throws IOException {
        try (InputStream in = exchange.getRequestBody()) {
            byte[] data = in.readNBytes((int) Math.min(maxBodyBytes, Integer.MAX_VALUE));
            // If more bytes remain, the body exceeded the limit.
            if (in.read() != -1) {
                return null;
            }
            return new String(data, StandardCharsets.UTF_8);
        }
    }
}
