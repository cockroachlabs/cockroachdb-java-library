package com.cockroachlabs.crdb.agent;

import com.cockroachlabs.crdb.Crdb;
import com.cockroachlabs.crdb.annotations.AgentTool;
import com.cockroachlabs.crdb.observe.CrdbEvents;
import com.cockroachlabs.crdb.observe.CrdbStats;
import com.cockroachlabs.crdb.spi.TableStorage;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A built-in provider of safe, read-only {@code @AgentTool}s: health, retry
 * statistics, and (when a {@link DataSource} is supplied) a Cockroach Plenum
 * storage summary.
 */
public final class CrdbOpsTools {

    private final DataSource dataSource;

    public CrdbOpsTools() {
        this(null);
    }

    public CrdbOpsTools(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** Liveness/health signal for the library surface. */
    @AgentTool("health")
    public Map<String, Object> health() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("status", "ok");
        m.put("datasourceConfigured", dataSource != null);
        return m;
    }

    /** Current retry / circuit-breaker counters. */
    @AgentTool("retry_stats")
    public CrdbStats retryStats() {
        return CrdbEvents.stats();
    }

    /**
     * Cockroach Plenum storage summary for the current database. Requires a
     * {@link DataSource}; delegates to {@code Crdb.storage(ds).summary()}.
     */
    @AgentTool("storage_summary")
    public List<Map<String, Object>> storageSummary() {
        if (dataSource == null) {
            throw new IllegalStateException("storage_summary requires a DataSource");
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (TableStorage t : Crdb.storage(dataSource).summary()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("schema", t.schema());
            row.put("table", t.table());
            row.put("rows", t.rows().isPresent() ? t.rows().getAsLong() : null);
            row.put("bytes", t.bytes().isPresent() ? t.bytes().getAsLong() : null);
            out.add(row);
        }
        return out;
    }
}
