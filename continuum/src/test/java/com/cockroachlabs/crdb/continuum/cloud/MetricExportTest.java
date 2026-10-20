package com.cockroachlabs.crdb.continuum.cloud;

import com.cockroachlabs.crdb.continuum.cloud.CloudClient.MetricExportConfig;
import com.cockroachlabs.crdb.continuum.cloud.CloudClient.MetricExportTarget;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MetricExportTest {

    @Test
    void datadogBuildsSiteAndApiKey() {
        MetricExportConfig cfg = MetricExport.datadog("key-1", "US1");
        assertEquals(MetricExportTarget.DATADOG, cfg.target());
        assertEquals("key-1", cfg.settings().get("api_key"));
        assertEquals("US1", cfg.settings().get("site"));
    }

    @Test
    void cloudWatchBuildsFieldsAndOmitsBlankRegion() {
        MetricExportConfig cfg = MetricExport.cloudWatch("arn:aws:iam::1:role/R", "crdb-logs", "us-east-1");
        assertEquals(MetricExportTarget.CLOUDWATCH, cfg.target());
        assertEquals("arn:aws:iam::1:role/R", cfg.settings().get("role_arn"));
        assertEquals("crdb-logs", cfg.settings().get("log_group_name"));
        assertEquals("us-east-1", cfg.settings().get("target_region"));
        assertFalse(MetricExport.cloudWatch("arn", "lg", "").settings().containsKey("target_region"));
    }

    @Test
    void prometheusHasNoSettings() {
        MetricExportConfig cfg = MetricExport.prometheus();
        assertEquals(MetricExportTarget.PROMETHEUS, cfg.target());
        assertTrue(cfg.settings().isEmpty());
    }

    @Test
    void healthReachableForSimulated() {
        assertTrue(CloudHealth.isReachable(new SimulatedCloudClient()));
    }

    @Test
    void healthFalseWhenListThrows() {
        CloudClient broken = new CloudClient() {
            @Override
            public java.util.List<Cluster> listClusters() {
                throw new RuntimeException("down");
            }

            @Override
            public Cluster getCluster(String clusterId) {
                throw new RuntimeException("down");
            }

            @Override
            public void configureMetricExport(String clusterId, MetricExportConfig config) {
                throw new RuntimeException("down");
            }
        };
        assertFalse(CloudHealth.isReachable(broken));
    }
}
