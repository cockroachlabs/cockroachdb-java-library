package com.cockroachlabs.crdb.continuum.aegis;

import com.cockroachlabs.crdb.spi.AegisClient;
import com.cockroachlabs.crdb.spi.AppEvent;
import com.cockroachlabs.crdb.spi.Finding;
import com.cockroachlabs.crdb.spi.Recommendation;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * In-memory Cockroach Aegis backend for development and tests. Returns a small
 * canned set of findings/recommendations and records every emitted
 * {@link AppEvent} so tests can assert on the exhaust.
 */
public final class SimulatedAegisClient implements AegisClient {

    private final List<AppEvent> emitted = new CopyOnWriteArrayList<>();
    private final List<Finding> findings;
    private final List<Recommendation> recommendations;

    public SimulatedAegisClient() {
        this.findings = List.of(
                new Finding("f-hotspot-001", "warning", "Range hotspot detected",
                        "A range is receiving disproportionate traffic relative to its peers."),
                new Finding("f-retry-002", "info", "Elevated serialization retries",
                        "Serialization retries are above baseline for the last hour."));
        this.recommendations = List.of(
                new Recommendation("r-split-001", "Relieve the hot range",
                        "Traffic is concentrated on a single range.",
                        "Introduce a hash-sharded index or split the range."),
                new Recommendation("r-qos-002", "Tag background workloads",
                        "Batch jobs are competing with OLTP for resources.",
                        "Annotate batch methods with @Workload(Priority.LOW)."));
    }

    @Override
    public List<Finding> findings() {
        return findings;
    }

    @Override
    public List<Recommendation> recommendations() {
        return recommendations;
    }

    @Override
    public void emit(AppEvent event) {
        emitted.add(event);
    }

    /** Test/inspection hook: all events emitted so far, in order. */
    public List<AppEvent> emittedEvents() {
        return List.copyOf(emitted);
    }
}
