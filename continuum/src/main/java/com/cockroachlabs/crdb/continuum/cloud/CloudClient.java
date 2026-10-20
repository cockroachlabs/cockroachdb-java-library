package com.cockroachlabs.crdb.continuum.cloud;

import java.util.List;
import java.util.Map;

/**
 * Client for the CockroachDB Cloud control-plane API (part of Cockroach
 * Continuum): list clusters and configure metric export.
 *
 * <p>Optional and independent of the core transaction path. A
 * {@link SimulatedCloudClient} is used whenever no API key is configured (see
 * {@link ContinuumCloudFactory}); set an API key to target the real API (default
 * base URL {@code https://cockroachlabs.cloud/api/v1}). Authentication uses a
 * service-account secret key sent as a Bearer token.
 */
public interface CloudClient {

    /** Returns the clusters visible to the configured service account. */
    List<Cluster> listClusters();

    /** Returns a single cluster by its control-plane ID. */
    Cluster getCluster(String clusterId);

    /** Enables a metric-export sink for a cluster. */
    void configureMetricExport(String clusterId, MetricExportConfig config);

    /** A CockroachDB Cloud cluster (control-plane view). */
    record Cluster(String id, String name, String plan, String state,
                   String cloudProvider, List<String> regions) {
    }

    /**
     * A Cockroach virtual cluster. {@code routingId} is used when connecting
     * (differs from the control-plane cluster ID); see
     * {@link VirtualClusterManager#routingConnString}.
     */
    record VirtualCluster(String name, String id, String routingId, String state) {
    }

    /** A Cloud metric-export sink. */
    enum MetricExportTarget {
        DATADOG, CLOUDWATCH, PROMETHEUS;

        public String path() {
            return name().toLowerCase();
        }
    }

    /** Metric-export configuration; {@code settings} holds target-specific fields. */
    record MetricExportConfig(MetricExportTarget target, Map<String, Object> settings) {
    }

    /** Configuration for the Cloud client. */
    record Config(boolean enabled, String baseUrl, String apiKey) {
        public Config {
            baseUrl = baseUrl == null ? "" : baseUrl;
            apiKey = apiKey == null ? "" : apiKey;
        }
    }
}
