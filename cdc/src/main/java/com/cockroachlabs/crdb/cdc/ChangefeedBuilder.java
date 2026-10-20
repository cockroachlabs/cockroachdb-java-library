package com.cockroachlabs.crdb.cdc;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Fluent builder for a CockroachDB {@code CREATE CHANGEFEED} statement.
 *
 * <p>Produces SQL of the form:
 * <pre>{@code
 * CREATE CHANGEFEED FOR TABLE <tables> INTO '<sink>' WITH <k = 'v'>, <flag>, ...
 * }</pre>
 *
 * <p>Example:
 * <pre>{@code
 * String sql = ChangefeedBuilder.forTables("orders", "payments")
 *         .intoWebhook("https://sink.example/cdc")
 *         .format("json")
 *         .updated()
 *         .diff()
 *         .resolved("10s")
 *         .toSql();
 * }</pre>
 *
 * <p>Table identifiers are validated to guard against injection; option values
 * are single-quote escaped. WITH options preserve insertion order so generated
 * SQL is deterministic.
 */
public final class ChangefeedBuilder {

    private static final Pattern IDENT_PART = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_$]*$");

    private final List<String> tables = new ArrayList<>();
    private final Map<String, String> options = new LinkedHashMap<>();
    private String sink;

    private ChangefeedBuilder() {
    }

    /** Begin a changefeed for one or more fully- or partially-qualified tables. */
    public static ChangefeedBuilder forTables(String... tables) {
        ChangefeedBuilder b = new ChangefeedBuilder();
        for (String t : tables) {
            b.addTable(t);
        }
        return b;
    }

    /** Add another table to the changefeed. */
    public ChangefeedBuilder addTable(String table) {
        tables.add(validateTable(table));
        return this;
    }

    /** Set the sink URI verbatim (e.g. {@code kafka://...}, {@code webhook-https://...}, {@code external://...}). */
    public ChangefeedBuilder into(String sinkUri) {
        this.sink = sinkUri;
        return this;
    }

    /** Set an HTTPS webhook sink; the required {@code webhook-} scheme prefix is added. */
    public ChangefeedBuilder intoWebhook(String httpsUrl) {
        this.sink = httpsUrl.startsWith("webhook-") ? httpsUrl : "webhook-" + httpsUrl;
        return this;
    }

    /** {@code WITH format = '<fmt>'} (e.g. {@code json}, {@code avro}, {@code csv}, {@code parquet}). */
    public ChangefeedBuilder format(String format) {
        return option("format", format);
    }

    /** {@code WITH envelope = '<env>'} (e.g. {@code wrapped}, {@code bare}, {@code key_only}, {@code row}). */
    public ChangefeedBuilder envelope(String envelope) {
        return option("envelope", envelope);
    }

    /** {@code WITH updated} — include the MVCC timestamp of each row change. */
    public ChangefeedBuilder updated() {
        return flag("updated");
    }

    /** {@code WITH diff} — include the {@code before} image of each changed row. */
    public ChangefeedBuilder diff() {
        return flag("diff");
    }

    /** {@code WITH resolved} — emit resolved-timestamp checkpoints at CockroachDB's default cadence. */
    public ChangefeedBuilder resolved() {
        return flag("resolved");
    }

    /** {@code WITH resolved = '<interval>'} — emit resolved checkpoints at the given interval (e.g. {@code 10s}). */
    public ChangefeedBuilder resolved(String interval) {
        return option("resolved", interval);
    }

    /** {@code WITH cursor = '<ts>'} — start the changefeed from a specific timestamp. */
    public ChangefeedBuilder cursor(String timestamp) {
        return option("cursor", timestamp);
    }

    /** {@code WITH initial_scan = '<mode>'} (e.g. {@code yes}, {@code no}, {@code only}). */
    public ChangefeedBuilder initialScan(String mode) {
        return option("initial_scan", mode);
    }

    /** {@code WITH key_in_value} — embed the primary key in the value payload. */
    public ChangefeedBuilder keyInValue() {
        return flag("key_in_value");
    }

    /** {@code WITH mvcc_timestamp} — include the MVCC timestamp on every emitted row. */
    public ChangefeedBuilder mvccTimestamp() {
        return flag("mvcc_timestamp");
    }

    /** Set an arbitrary {@code WITH key = 'value'} option (value is escaped). */
    public ChangefeedBuilder option(String key, String value) {
        options.put(key, value);
        return this;
    }

    /** Set an arbitrary boolean {@code WITH} option (no value). */
    public ChangefeedBuilder flag(String key) {
        options.put(key, null);
        return this;
    }

    /** The tables this changefeed covers. */
    public List<String> tables() {
        return List.copyOf(tables);
    }

    /** The configured sink URI. */
    public String sink() {
        return sink;
    }

    /** A copy of the WITH options (null value == flag). */
    public Map<String, String> options() {
        return new LinkedHashMap<>(options);
    }

    /** Render the {@code CREATE CHANGEFEED} statement. */
    public String toSql() {
        if (tables.isEmpty()) {
            throw new IllegalStateException("changefeed requires at least one table");
        }
        if (sink == null || sink.isBlank()) {
            throw new IllegalStateException("changefeed requires a sink (call into(...) or intoWebhook(...))");
        }
        StringBuilder sb = new StringBuilder("CREATE CHANGEFEED FOR TABLE ");
        sb.append(String.join(", ", tables));
        sb.append(" INTO '").append(escape(sink)).append('\'');
        if (!options.isEmpty()) {
            sb.append(" WITH ");
            List<String> parts = new ArrayList<>(options.size());
            for (Map.Entry<String, String> e : options.entrySet()) {
                if (e.getValue() == null) {
                    parts.add(e.getKey());
                } else {
                    parts.add(e.getKey() + " = '" + escape(e.getValue()) + "'");
                }
            }
            sb.append(String.join(", ", parts));
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return toSql();
    }

    private static String validateTable(String table) {
        if (table == null || table.isBlank()) {
            throw new IllegalArgumentException("table name must not be blank");
        }
        for (String part : table.split("\\.")) {
            if (!IDENT_PART.matcher(part).matches()) {
                throw new IllegalArgumentException("invalid table identifier: " + table);
            }
        }
        return table;
    }

    private static String escape(String value) {
        return value.replace("'", "''");
    }
}
