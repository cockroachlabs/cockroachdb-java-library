package com.cockroachlabs.crdb.continuum.cloud;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory {@link CloudClient} for development and tests. Serves a small canned
 * set of clusters and records metric-export calls. Selected automatically when no
 * Cloud API key is configured.
 */
public final class SimulatedCloudClient implements CloudClient {

    private final List<Cluster> clusters = List.of(
            new Cluster("00000000-0000-0000-0000-000000000001", "dev-cluster",
                    "STANDARD", "CREATED", "GCP", List.of("us-east1")));

    private final Map<String, MetricExportConfig> exports = new ConcurrentHashMap<>();

    @Override
    public List<Cluster> listClusters() {
        return new ArrayList<>(clusters);
    }

    @Override
    public Cluster getCluster(String clusterId) {
        return clusters.stream()
                .filter(c -> c.id().equals(clusterId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("cloud: cluster not found: " + clusterId));
    }

    @Override
    public void configureMetricExport(String clusterId, MetricExportConfig config) {
        if (config == null || config.target() == null) {
            throw new IllegalArgumentException("cloud: metric export target is required");
        }
        exports.put(clusterId, config);
    }

    /** The metric-export configs recorded so far, keyed by cluster ID. */
    public Map<String, MetricExportConfig> metricExports() {
        return Map.copyOf(exports);
    }
}
