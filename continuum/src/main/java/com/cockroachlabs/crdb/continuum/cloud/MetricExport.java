package com.cockroachlabs.crdb.continuum.cloud;

import com.cockroachlabs.crdb.continuum.cloud.CloudClient.MetricExportConfig;
import com.cockroachlabs.crdb.continuum.cloud.CloudClient.MetricExportTarget;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Typed builders for the three metric-export sinks. Each returns a
 * {@link MetricExportConfig} ready to pass to
 * {@link CloudClient#configureMetricExport(String, MetricExportConfig)}, so callers
 * don't have to remember the Cloud API's field names. The raw settings map remains
 * available for sinks or fields not covered here (e.g. Azure Monitor).
 */
public final class MetricExport {

    private MetricExport() {
    }

    /** Builds a Datadog config. {@code site} is the Datadog site (e.g. {@code US1}). */
    public static MetricExportConfig datadog(String apiKey, String site) {
        return new MetricExportConfig(MetricExportTarget.DATADOG,
                Map.of("api_key", apiKey, "site", site));
    }

    /**
     * Builds an Amazon CloudWatch config. {@code roleArn} is the
     * CockroachCloudMetricsExportRole ARN; {@code targetRegion} may be blank to use
     * the cluster's region.
     */
    public static MetricExportConfig cloudWatch(String roleArn, String logGroupName, String targetRegion) {
        Map<String, Object> settings = new LinkedHashMap<>();
        settings.put("role_arn", roleArn);
        settings.put("log_group_name", logGroupName);
        if (targetRegion != null && !targetRegion.isBlank()) {
            settings.put("target_region", targetRegion);
        }
        return new MetricExportConfig(MetricExportTarget.CLOUDWATCH, settings);
    }

    /** Builds a Prometheus config (enabled without extra settings). */
    public static MetricExportConfig prometheus() {
        return new MetricExportConfig(MetricExportTarget.PROMETHEUS, Map.of());
    }
}
