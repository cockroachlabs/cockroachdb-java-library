package com.cockroachlabs.crdb.multiregion;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import javax.sql.DataSource;

import com.cockroachlabs.crdb.annotations.CrdbRegion;
import com.cockroachlabs.crdb.annotations.GlobalTable;
import com.cockroachlabs.crdb.annotations.RegionalByRow;
import com.cockroachlabs.crdb.multiregion.MultiregionState.TableInfo;
import com.cockroachlabs.crdb.multiregion.MultiregionState.TableName;
import com.cockroachlabs.crdb.multiregion.MultiregionTable.Kind;

/**
 * Compares annotated entities with the live multi-region configuration of their database
 * and tables, and suggests SQL for every difference. It only reads; it never changes the
 * schema.
 *
 * <p>All queries use {@code SHOW} statements and {@code information_schema}, not
 * {@code crdb_internal}, which recent CockroachDB versions restrict by default.
 *
 * <p>Unlike the Spring starter — which reads entities from Hibernate's metamodel and runs
 * automatically at startup — this library has no ORM and no startup hook, so
 * {@link #verify} is an explicit entry point ({@link com.cockroachlabs.crdb.Crdb#verifyLocality})
 * and entities are discovered by reflecting over the classes the caller passes in.
 */
public final class MultiregionCheck {

    private static final Logger LOG = Logger.getLogger(MultiregionCheck.class.getName());

    private static final String REGION_TYPE = "crdb_internal_region";

    private static final String IMPLICIT_REGION_COLUMN = "crdb_region";

    private static final String REGIONAL_BY_ROW = "REGIONAL BY ROW";

    private MultiregionCheck() {
    }

    // ---- entry point -----------------------------------------------------

    /**
     * Connect, run the check for {@code entities}, and react per {@code mode}: in
     * {@link Mode#WARN} log any problems and return the report; in {@link Mode#VALIDATE}, or on
     * any contradiction, throw {@link IllegalStateException} whose message is the report. Skips
     * quietly (returning an empty report) when the database is not CockroachDB, or when there are
     * no entities. {@link Mode#NONE} is off and returns an empty report without connecting.
     */
    public static MultiregionReport verify(DataSource ds, Mode mode, Class<?>... entities) {
        if (mode == Mode.NONE) {
            return new MultiregionReport();
        }
        List<MultiregionTable> tables = scan(entities);
        if (tables.isEmpty()) {
            return new MultiregionReport();
        }

        MultiregionReport report;
        try (Connection connection = ds.getConnection()) {
            if (!isCockroachDB(connection)) {
                LOG.fine(() -> "CockroachDB multiregion check (" + MultiregionReport.SOURCE
                        + ") skipped: the database is not CockroachDB");
                return new MultiregionReport();
            }
            MultiregionState state = load(connection, tables);
            report = evaluate(state, tables);
        } catch (SQLException ex) {
            if (mode == Mode.VALIDATE) {
                throw new IllegalStateException("CockroachDB multiregion check (" + MultiregionReport.SOURCE
                        + ") could not read the database", ex);
            }
            LOG.log(Level.WARNING, ex, () -> "CockroachDB multiregion check (" + MultiregionReport.SOURCE
                    + ") could not read the database: " + ex.getMessage());
            return new MultiregionReport();
        }

        react(report, mode, tables.size());
        return report;
    }

    /**
     * Log or throw for {@code report} per {@code mode}. A contradiction throws even in
     * {@link Mode#WARN}; only {@link Mode#NONE} (which never reaches here) lets one pass. Kept
     * package-private and separate so the warn-vs-throw decision is unit-testable without a database.
     */
    static void react(MultiregionReport report, Mode mode, int entityCount) {
        if (report.isEmpty()) {
            LOG.fine(() -> "CockroachDB multiregion check (" + MultiregionReport.SOURCE + ") passed for "
                    + entityCount + " entities");
        } else if (mode == Mode.VALIDATE || report.hasContradictions()) {
            throw new IllegalStateException(report.format(mode));
        } else {
            LOG.warning(report.format(mode));
        }
    }

    // ---- entity discovery (reflection; replaces Hibernate metamodel) -----

    /** Reflects over {@code entities}, reading each one's multi-region annotations and region column. */
    static List<MultiregionTable> scan(Class<?>... entities) {
        List<MultiregionTable> tables = new ArrayList<>();
        for (Class<?> type : entities) {
            tables.add(read(type));
        }
        return tables;
    }

    private static MultiregionTable read(Class<?> type) {
        GlobalTable global = find(type, GlobalTable.class);
        RegionalByRow regionalByRow = find(type, RegionalByRow.class);
        Kind kind = global != null && regionalByRow != null
                ? Kind.BOTH
                : global != null
                    ? Kind.GLOBAL
                    : regionalByRow != null
                        ? Kind.REGIONAL_BY_ROW
                        : Kind.NONE;

        String declared = "";
        if (global != null && !global.table().isBlank()) {
            declared = global.table();
        } else if (regionalByRow != null && !regionalByRow.table().isBlank()) {
            declared = regionalByRow.table();
        }
        String rendered = declared.isBlank() ? type.getSimpleName() : declared;
        List<String> name = Identifiers.parse(rendered);
        String table = name.get(name.size() - 1);
        String schema = name.size() > 1 ? name.get(name.size() - 2) : null;

        String regionColumn = null;
        if (kind == Kind.REGIONAL_BY_ROW) {
            regionColumn = regionColumn(type, regionalByRow);
        }
        return new MultiregionTable(className(type), schema, table, kind, regionColumn);
    }

    /**
     * The entity's fully qualified name, as a developer types it into an IDE to find the class:
     * the canonical name ({@code com.example.Outer.Inner}), or the binary name for a class that
     * has none.
     */
    static String className(Class<?> type) {
        String canonical = type.getCanonicalName();
        return canonical != null ? canonical : type.getName();
    }

    /**
     * The region column for a {@code @RegionalByRow} entity: {@code regionColumn()} if set,
     * otherwise the column of the first {@code @CrdbRegion} field (its field name, folded like an
     * unquoted identifier), otherwise {@code null} (the implicit {@code crdb_region} is assumed).
     */
    private static String regionColumn(Class<?> type, RegionalByRow regionalByRow) {
        if (regionalByRow != null && !regionalByRow.regionColumn().isBlank()) {
            return Identifiers.parse(regionalByRow.regionColumn()).get(0);
        }
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field field : c.getDeclaredFields()) {
                if (field.isAnnotationPresent(CrdbRegion.class)) {
                    return Identifiers.parse(field.getName()).get(0);
                }
            }
        }
        return null;
    }

    /** Finds {@code annotation} on {@code type} or any superclass, so a subclass shares its parent's. */
    private static <A extends Annotation> A find(Class<?> type, Class<A> annotation) {
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            A found = c.getDeclaredAnnotation(annotation);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    // ---- live-schema reads (ported verbatim from the Spring starter) -----

    /**
     * Whether {@code connection} is to CockroachDB. The check must stay quiet for applications
     * whose tests run against another database.
     */
    static boolean isCockroachDB(Connection connection) {
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT version()")) {
            return rs.next() && rs.getString(1) != null && rs.getString(1).contains("CockroachDB");
        } catch (SQLException ex) {
            return false;
        }
    }

    /** Reads what {@link #evaluate} needs for {@code tables} over {@code connection}. */
    static MultiregionState load(Connection connection, List<MultiregionTable> tables) throws SQLException {
        String database;
        String currentSchema;
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT current_database(), current_schema()")) {
            rs.next();
            database = rs.getString(1);
            currentSchema = rs.getString(2) != null ? rs.getString(2) : "public";
        }

        String primaryRegion = null;
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT primary_region FROM [SHOW DATABASES] WHERE database_name = ?")) {
            statement.setString(1, database);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    primaryRegion = rs.getString(1);
                }
            }
        }

        List<String> clusterRegions = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT region FROM [SHOW REGIONS FROM CLUSTER] ORDER BY region")) {
            while (rs.next()) {
                clusterRegions.add(rs.getString(1));
            }
        }

        Set<String> keywords = new HashSet<>();
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(
                     "SELECT word FROM pg_get_keywords() WHERE catcode IN ('R', 'T')")) {
            while (rs.next()) {
                keywords.add(rs.getString(1));
            }
        }

        Set<TableName> wanted = new HashSet<>();
        for (MultiregionTable table : tables) {
            wanted.add(nameOf(table, currentSchema));
        }

        Map<TableName, TableInfo> found = new HashMap<>();
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(
                     "SELECT schema_name, table_name, locality FROM [SHOW TABLES] WHERE type = 'table'")) {
            while (rs.next()) {
                TableName name = new TableName(rs.getString(1), rs.getString(2));
                if (wanted.contains(name)) {
                    found.put(name, new TableInfo(rs.getString(3), Map.of()));
                }
            }
        }

        for (MultiregionTable table : tables) {
            TableName name = nameOf(table, currentSchema);
            TableInfo info = found.get(name);
            if (table.kind() == Kind.REGIONAL_BY_ROW && info != null) {
                found.put(name, new TableInfo(info.locality(), columnTypes(connection, name)));
            }
        }

        return new MultiregionState(database, currentSchema, primaryRegion,
                List.copyOf(clusterRegions), found, keywords);
    }

    private static Map<String, String> columnTypes(Connection connection, TableName name) throws SQLException {
        Map<String, String> types = new HashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT column_name, crdb_sql_type FROM information_schema.columns "
                        + "WHERE table_catalog = current_database() AND table_schema = ? AND table_name = ?")) {
            statement.setString(1, name.schema());
            statement.setString(2, name.table());
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    types.put(rs.getString(1), rs.getString(2));
                }
            }
        }
        return types;
    }

    // ---- diff (ported verbatim from the Spring starter) ------------------

    /**
     * Lists every difference between {@code tables} and {@code state}, with SQL to fix the schema
     * or a description of the annotation to fix. {@code tables} holds every entity, annotated or
     * not: a {@code GLOBAL} or {@code REGIONAL BY ROW} table needs its annotation too.
     */
    static MultiregionReport evaluate(MultiregionState state, List<MultiregionTable> tables) {
        MultiregionReport report = new MultiregionReport();
        Set<String> keywords = state.keywords();
        boolean multiregion = state.primaryRegion() != null;
        long annotated = tables.stream().filter(table -> table.kind() != Kind.NONE).count();

        if (!multiregion && annotated > 0) {
            String database = Identifiers.quote(state.database(), keywords);
            String names = tables.stream().filter(table -> table.kind() != Kind.NONE)
                    .map(MultiregionTable::entity).collect(Collectors.joining(", "));
            String affected = "@GlobalTable/@RegionalByRow on " + count(annotated, "entity", "entities")
                    + " (" + names + ") have no effect.";
            if (state.clusterRegions().isEmpty()) {
                report.problem("Database " + database + " is not multi-region; " + affected
                        + "\nThe cluster has no regions, so the database can't be made multi-region yet:"
                        + " start every node with --locality=region=<region>.");
            } else {
                report.problem("Database " + database + " is not multi-region; " + affected
                        + "\nThe cluster has regions: " + String.join(", ", state.clusterRegions())
                        + ". Choose the primary region deliberately; the SQL below uses the first one.");
                List<String> regions = state.clusterRegions();
                report.statement("ALTER DATABASE " + database + " SET PRIMARY REGION " + regionLiteral(regions.get(0)) + ";");
                for (String region : regions.subList(1, regions.size())) {
                    report.statement("ALTER DATABASE " + database + " ADD REGION " + regionLiteral(region) + ";");
                }
            }
        }

        Map<TableName, List<MultiregionTable>> byTable = new LinkedHashMap<>();
        for (MultiregionTable table : tables) {
            byTable.computeIfAbsent(nameOf(table, state.currentSchema()), name -> new ArrayList<>()).add(table);
        }

        for (Map.Entry<TableName, List<MultiregionTable>> entry : byTable.entrySet()) {
            TableName name = entry.getKey();
            String qualified = Identifiers.quote(name.schema(), keywords) + "."
                    + Identifiers.quote(name.table(), keywords);
            TableInfo info = state.tables().get(name);

            List<MultiregionTable> annotatedEntities = entry.getValue().stream()
                    .filter(table -> table.kind() != Kind.NONE)
                    .toList();
            Set<Kind> kinds = annotatedEntities.stream()
                    .map(MultiregionTable::kind)
                    .collect(Collectors.toCollection(LinkedHashSet::new));

            if (kinds.isEmpty()) {
                unannotated(report, qualified, info, entry.getValue(), multiregion);
            } else if (kinds.size() > 1 || kinds.contains(Kind.BOTH)) {
                conflicting(report, qualified, annotatedEntities);
            } else {
                MultiregionTable table = annotatedEntities.get(0);
                if (table.kind() == Kind.GLOBAL) {
                    global(report, qualified, info, table, multiregion);
                } else {
                    regionalByRow(report, state, table, qualified, info, multiregion);
                }
            }
        }
        return report;
    }

    private static void global(MultiregionReport report, String qualified, TableInfo info,
                               MultiregionTable table, boolean multiregion) {
        if (info == null) {
            missing(report, qualified, "@GlobalTable", table);
        } else if (!multiregion || !"GLOBAL".equals(info.locality())) {
            if (multiregion) {
                String problem = "Table " + qualified + " (@GlobalTable " + table.entity() + ") is "
                        + info.locality() + ", not GLOBAL.";
                if (regionalByRowColumn(info.locality()) != null) {
                    report.contradiction(problem);
                } else {
                    report.problem(problem);
                }
            }
            report.statement("ALTER TABLE " + qualified + " SET LOCALITY GLOBAL;");
        }
    }

    /**
     * An entity without either annotation whose table is {@code GLOBAL} or {@code REGIONAL BY ROW}:
     * the code, not the schema, needs fixing.
     */
    private static void unannotated(MultiregionReport report, String qualified, TableInfo info,
                                    List<MultiregionTable> entities, boolean multiregion) {
        if (info == null || !multiregion) {
            return;
        }
        String names = entities.stream().map(MultiregionTable::entity).collect(Collectors.joining(", "));
        if ("GLOBAL".equals(info.locality())) {
            report.problem("Table " + qualified + " is GLOBAL, but entity " + names
                    + " has no @GlobalTable. Add @GlobalTable to it.");
        } else if (regionalByRowColumn(info.locality()) != null) {
            String column = regionalByRowColumn(info.locality());
            report.problem("Table " + qualified + " is " + info.locality() + ", but entity " + names
                    + " has no @RegionalByRow. Add @RegionalByRow to it, and @CrdbRegion to the field mapped to "
                    + column + " so rows are homed in the transaction's region.");
        }
    }

    private static void conflicting(MultiregionReport report, String qualified, List<MultiregionTable> entities) {
        if (entities.size() == 1) {
            report.contradiction("Entity " + entities.get(0).entity()
                    + " is annotated with both @GlobalTable and @RegionalByRow; keep one.");
            return;
        }
        String described = entities.stream()
                .map(table -> table.entity() + " (" + switch (table.kind()) {
                    case GLOBAL -> "@GlobalTable";
                    case REGIONAL_BY_ROW -> "@RegionalByRow";
                    default -> "@GlobalTable and @RegionalByRow";
                } + ")")
                .collect(Collectors.joining(", "));
        report.contradiction("Table " + qualified + " is mapped by entities with conflicting annotations: "
                + described + ". Annotate them alike.");
    }

    private static void regionalByRow(MultiregionReport report, MultiregionState state, MultiregionTable table,
                                      String qualified, TableInfo info, boolean multiregion) {
        if (info == null) {
            missing(report, qualified, "@RegionalByRow", table);
            return;
        }
        String wantedColumn = table.regionColumn();
        String currentColumn = regionalByRowColumn(info.locality());
        if (multiregion && currentColumn != null && (wantedColumn == null || wantedColumn.equals(currentColumn))) {
            return;
        }

        String column = wantedColumn != null ? wantedColumn : IMPLICIT_REGION_COLUMN;
        String quotedColumn = Identifiers.quote(column, state.keywords());
        String type = info.columnTypes().get(column);
        String description = "Table " + qualified + " (@RegionalByRow " + table.entity() + ")";
        String target = IMPLICIT_REGION_COLUMN.equals(column)
                ? REGIONAL_BY_ROW
                : REGIONAL_BY_ROW + " AS " + quotedColumn;
        boolean global = multiregion && "GLOBAL".equals(info.locality());
        if (global) {
            report.contradiction(description + " is GLOBAL, not " + target + ".");
        }
        if (type != null && !isRegionType(type)) {
            report.problem(description + ": column " + quotedColumn + " is " + type
                    + ", but REGIONAL BY ROW needs a " + REGION_TYPE + " column. Change it with your migrations.");
            return;
        }
        if (type == null && !IMPLICIT_REGION_COLUMN.equals(column)) {
            report.problem(description + ": column " + quotedColumn
                    + " of the @CrdbRegion field does not exist. Create it, as a " + REGION_TYPE
                    + " column, with your migrations.");
            return;
        }

        if (multiregion && !global) {
            report.problem(description + " is " + info.locality() + ", not " + target + ".");
        }
        report.statement("ALTER TABLE " + qualified + " SET LOCALITY " + target + ";  -- rewrites the table");
    }

    private static void missing(MultiregionReport report, String qualified, String annotation, MultiregionTable table) {
        report.problem("Table " + qualified + " (" + annotation + " " + table.entity()
                + ") does not exist. Create it with your migrations.");
    }

    /**
     * The region column of a {@code REGIONAL BY ROW} locality, or {@code null} for any other
     * locality.
     */
    static String regionalByRowColumn(String locality) {
        if (locality == null || !locality.startsWith(REGIONAL_BY_ROW)) {
            return null;
        }
        String rest = locality.substring(REGIONAL_BY_ROW.length()).trim();
        if (rest.isEmpty()) {
            return IMPLICIT_REGION_COLUMN;
        }
        if (rest.startsWith("AS ")) {
            return Identifiers.parse(rest.substring(3).trim()).get(0);
        }
        return null;
    }

    private static boolean isRegionType(String type) {
        return type.equals(REGION_TYPE) || type.endsWith("." + REGION_TYPE);
    }

    private static TableName nameOf(MultiregionTable table, String currentSchema) {
        return new TableName(table.schema() != null ? table.schema() : currentSchema, table.table());
    }

    private static String regionLiteral(String region) {
        return '"' + region.replace("\"", "\"\"") + '"';
    }

    private static String count(long n, String one, String many) {
        return n + " " + (n == 1 ? one : many);
    }
}
