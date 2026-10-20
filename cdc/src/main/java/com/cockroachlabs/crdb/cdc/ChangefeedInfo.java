package com.cockroachlabs.crdb.cdc;

import java.time.Instant;

/**
 * Metadata about a changefeed job created via {@link ChangefeedManager}.
 *
 * @param jobId   the CockroachDB job id
 * @param status  the job status at creation time (typically {@code running})
 * @param created when the job was created (client clock)
 */
public record ChangefeedInfo(long jobId, String status, Instant created) {
}
