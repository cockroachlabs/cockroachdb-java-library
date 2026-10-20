package com.cockroachlabs.crdb.cdc;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * The top-level JSON structure sent by CockroachDB's webhook sink. Each HTTP
 * POST from a changefeed carries exactly one envelope: either a batch of row
 * changes ({@code payload}) or a resolved-timestamp checkpoint ({@code resolved}).
 *
 * @param payload  the batched row changes (empty/absent for a resolved checkpoint)
 * @param length   the number of payload entries, as reported by CockroachDB
 * @param resolved the resolved timestamp, present only on checkpoint envelopes
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CdcEnvelope(
        List<CdcPayload> payload,
        Integer length,
        String resolved) {

    /**
     * Whether this envelope is a resolved-timestamp checkpoint (no data changes,
     * just a high-watermark).
     */
    public boolean isResolved() {
        return resolved != null && !resolved.isBlank()
                && (payload == null || payload.isEmpty());
    }
}
