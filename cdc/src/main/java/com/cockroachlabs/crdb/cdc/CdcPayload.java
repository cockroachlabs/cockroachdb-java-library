package com.cockroachlabs.crdb.cdc;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Map;

/**
 * A single row change within a changefeed webhook delivery, as emitted by
 * CockroachDB's {@code wrapped} JSON envelope.
 *
 * @param after   the row's new column values, or {@code null} for a delete
 * @param before  the row's prior column values; present only {@code WITH diff}
 * @param key     the primary-key column values
 * @param topic   the source table (topic) name
 * @param updated the MVCC timestamp of the change; present only {@code WITH updated}
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CdcPayload(
        Map<String, Object> after,
        Map<String, Object> before,
        List<Object> key,
        String topic,
        String updated) {
}
