package com.cockroachlabs.crdb.cdc;

import java.util.List;
import java.util.Map;

/**
 * A decoded, application-facing change event: the raw {@link CdcPayload} with its
 * {@link Operation} classified.
 *
 * @param operation the classified change type
 * @param table     the source table
 * @param key       the primary-key values
 * @param after     the new row image, or {@code null} for a delete
 * @param before    the prior row image, or {@code null} when diff is disabled
 * @param updated   the MVCC timestamp, or {@code null} when updated is disabled
 */
public record CdcEvent(
        Operation operation,
        String table,
        List<Object> key,
        Map<String, Object> after,
        Map<String, Object> before,
        String updated) {

    /** Build an event from a raw changefeed payload, inferring the operation. */
    public static CdcEvent from(CdcPayload payload) {
        return new CdcEvent(
                Operation.fromImages(payload.before(), payload.after()),
                payload.topic(),
                payload.key(),
                payload.after(),
                payload.before(),
                payload.updated());
    }
}
