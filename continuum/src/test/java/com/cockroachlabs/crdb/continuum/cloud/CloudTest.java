package com.cockroachlabs.crdb.continuum.cloud;

import com.cockroachlabs.crdb.continuum.cloud.CloudClient.Cluster;
import com.cockroachlabs.crdb.continuum.cloud.CloudClient.Config;
import com.cockroachlabs.crdb.continuum.cloud.CloudClient.MetricExportConfig;
import com.cockroachlabs.crdb.continuum.cloud.CloudClient.MetricExportTarget;
import com.cockroachlabs.crdb.continuum.cloud.CloudClient.VirtualCluster;
import com.cockroachlabs.crdb.continuum.cloud.HttpCloudClient.Response;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Unit tests for the Continuum Cloud client — fakes only, no live server/DB. */
final class CloudTest {

    /** Records every request and replies from a scripted queue. */
    private static final class FakeTransport implements HttpCloudClient.Transport {
        final List<String> methods = new ArrayList<>();
        final List<String> urls = new ArrayList<>();
        final List<Map<String, String>> headers = new ArrayList<>();
        final List<String> bodies = new ArrayList<>();
        private final List<Response> replies = new ArrayList<>();
        private int i = 0;

        FakeTransport reply(int status, String body) {
            replies.add(new Response(status, body));
            return this;
        }

        @Override
        public Response send(String method, String url, Map<String, String> h, String body) {
            methods.add(method);
            urls.add(url);
            headers.add(h);
            bodies.add(body);
            return replies.get(i++);
        }
    }

    // ---- factory selection ----

    @Test
    void factoryReturnsSimulatedWhenDisabledOrKeyless() {
        assertTrue(ContinuumCloudFactory.make(new Config(false, "", "key")) instanceof SimulatedCloudClient);
        assertTrue(ContinuumCloudFactory.make(new Config(true, "", "")) instanceof SimulatedCloudClient);
    }

    @Test
    void factoryReturnsHttpWhenEnabledWithKey() {
        var client = ContinuumCloudFactory.make(new Config(true, "", "secret"), new FakeTransport());
        assertTrue(client instanceof HttpCloudClient);
    }

    // ---- simulated ----

    @Test
    void simulatedServesCannedClusterAndRecordsExport() {
        var sim = new SimulatedCloudClient();
        List<Cluster> clusters = sim.listClusters();
        assertEquals(1, clusters.size());
        assertEquals("dev-cluster", clusters.get(0).name());

        var cfg = new MetricExportConfig(MetricExportTarget.DATADOG, Map.of("api_key", "x"));
        sim.configureMetricExport(clusters.get(0).id(), cfg);
        assertSame(cfg, sim.metricExports().get(clusters.get(0).id()));
    }

    // ---- HTTP client ----

    @Test
    void httpListClustersParsesAndSendsBearer() {
        var t = new FakeTransport().reply(200, """
                {"clusters":[
                  {"id":"c1","name":"prod","plan":"ADVANCED","state":"CREATED",
                   "cloud_provider":"AWS","regions":["us-east-1","us-west-2"]}
                ]}""");
        var client = new HttpCloudClient(new Config(true, "", "secret"), t);

        List<Cluster> out = client.listClusters();
        assertEquals(1, out.size());
        Cluster c = out.get(0);
        assertEquals("c1", c.id());
        assertEquals("prod", c.name());
        assertEquals("AWS", c.cloudProvider());
        assertEquals(List.of("us-east-1", "us-west-2"), c.regions());

        assertEquals("GET", t.methods.get(0));
        assertTrue(t.urls.get(0).endsWith("/clusters"));
        assertEquals("Bearer secret", t.headers.get(0).get("Authorization"));
    }

    @Test
    void httpGetClusterUsesIdPath() {
        var t = new FakeTransport().reply(200, """
                {"id":"abc","name":"one","plan":"STANDARD","state":"CREATED"}""");
        var client = new HttpCloudClient(new Config(true, "https://example.test/api/v1/", "k"), t);

        Cluster c = client.getCluster("abc");
        assertEquals("abc", c.id());
        assertEquals("https://example.test/api/v1/clusters/abc", t.urls.get(0));
    }

    @Test
    void httpConfigureMetricExportPostsTargetPathAndSettings() {
        var t = new FakeTransport().reply(200, "{}");
        var client = new HttpCloudClient(new Config(true, "", "k"), t);

        client.configureMetricExport("c1",
                new MetricExportConfig(MetricExportTarget.CLOUDWATCH, Map.of("region", "us-east-1")));

        assertEquals("POST", t.methods.get(0));
        assertTrue(t.urls.get(0).endsWith("/clusters/c1/metricexport/cloudwatch"));
        assertTrue(t.bodies.get(0).contains("us-east-1"));
    }

    @Test
    void httpNon2xxThrows() {
        var t = new FakeTransport().reply(403, "forbidden");
        var client = new HttpCloudClient(new Config(true, "", "k"), t);
        var ex = assertThrows(RuntimeException.class, client::listClusters);
        assertTrue(ex.getMessage().contains("403"));
    }

    // ---- virtual cluster manager ----

    /** Captures executed DDL and serves scripted query rows. */
    private static final class FakeExecutor implements VirtualClusterManager.SqlExecutor {
        final List<String> executed = new ArrayList<>();
        List<String[]> rows = List.of();

        @Override
        public void execute(String sql) {
            executed.add(sql);
        }

        @Override
        public List<String[]> query(String sql) {
            executed.add(sql);
            return rows;
        }
    }

    @Test
    void vclusterCreateDropAndListValidate() {
        var exec = new FakeExecutor();
        exec.rows = List.of(new String[] {"3", "analytics"}, new String[] {"4", "billing"});
        var mgr = new VirtualClusterManager(exec);

        mgr.create("analytics");
        mgr.drop("billing");
        List<VirtualCluster> list = mgr.list();

        assertEquals("CREATE VIRTUAL CLUSTER analytics", exec.executed.get(0));
        assertEquals("DROP VIRTUAL CLUSTER billing", exec.executed.get(1));
        assertEquals("SHOW VIRTUAL CLUSTERS", exec.executed.get(2));
        assertEquals(2, list.size());
        assertEquals("analytics", list.get(0).name());
        assertEquals("3", list.get(0).id());
    }

    @Test
    void vclusterRejectsInvalidName() {
        var mgr = new VirtualClusterManager(new FakeExecutor());
        assertThrows(IllegalArgumentException.class, () -> mgr.create("bad name; DROP"));
        assertThrows(IllegalArgumentException.class, () -> mgr.create("1leading"));
    }

    @Test
    void routingConnStringAppendsOption() {
        assertEquals(
                "postgresql://h:26257/db?options=-ccluster%3Danalytics",
                VirtualClusterManager.routingConnString("postgresql://h:26257/db", "analytics"));
        assertEquals(
                "postgresql://h:26257/db?sslmode=verify-full&options=-ccluster%3Danalytics",
                VirtualClusterManager.routingConnString(
                        "postgresql://h:26257/db?sslmode=verify-full", "analytics"));
    }
}
