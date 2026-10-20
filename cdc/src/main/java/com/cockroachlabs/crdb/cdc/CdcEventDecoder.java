package com.cockroachlabs.crdb.cdc;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses CockroachDB changefeed webhook bodies into {@link CdcEnvelope}s and
 * decodes their payloads into application-facing {@link CdcEvent}s. Thread-safe:
 * the underlying Jackson {@link ObjectMapper} is safe for concurrent reads.
 */
public final class CdcEventDecoder {

    private final ObjectMapper mapper;

    public CdcEventDecoder() {
        this(new ObjectMapper());
    }

    public CdcEventDecoder(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /** Parse a raw JSON webhook body into an envelope. */
    public CdcEnvelope decode(String json) {
        try {
            return mapper.readValue(json, CdcEnvelope.class);
        } catch (IOException e) {
            throw new UncheckedIOException("cdc: failed to parse changefeed envelope", e);
        }
    }

    /** Parse a raw JSON webhook body from a stream into an envelope. */
    public CdcEnvelope decode(InputStream json) {
        try {
            return mapper.readValue(json, CdcEnvelope.class);
        } catch (IOException e) {
            throw new UncheckedIOException("cdc: failed to parse changefeed envelope", e);
        }
    }

    /** Decode an envelope's payloads into events (empty for a resolved checkpoint). */
    public List<CdcEvent> toEvents(CdcEnvelope envelope) {
        if (envelope.payload() == null || envelope.payload().isEmpty()) {
            return List.of();
        }
        List<CdcEvent> events = new ArrayList<>(envelope.payload().size());
        for (CdcPayload p : envelope.payload()) {
            events.add(CdcEvent.from(p));
        }
        return events;
    }

    /** Convenience: decode a raw JSON body straight into events. */
    public List<CdcEvent> decodeEvents(String json) {
        return toEvents(decode(json));
    }
}
