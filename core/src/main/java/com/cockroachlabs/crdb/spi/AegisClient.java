package com.cockroachlabs.crdb.spi;

import java.util.List;

/**
 * Cockroach Aegis client: read-first access to autonomous-ops findings and
 * recommendations, plus an {@link #emit(AppEvent)} channel for app telemetry.
 * Reachable via {@link com.cockroachlabs.crdb.Crdb#aegis(AegisConfig)}.
 */
public interface AegisClient {

    /** Current findings for the cluster. */
    List<Finding> findings();

    /** Current recommendations for the cluster. */
    List<Recommendation> recommendations();

    /** Emit an application operational signal to Aegis. */
    void emit(AppEvent event);
}
