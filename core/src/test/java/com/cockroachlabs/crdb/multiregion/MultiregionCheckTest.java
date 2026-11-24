package com.cockroachlabs.crdb.multiregion;

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.cockroachlabs.crdb.annotations.CrdbRegion;
import com.cockroachlabs.crdb.annotations.GlobalTable;
import com.cockroachlabs.crdb.annotations.RegionalByRow;
import com.cockroachlabs.crdb.multiregion.MultiregionState.TableInfo;
import com.cockroachlabs.crdb.multiregion.MultiregionState.TableName;
import com.cockroachlabs.crdb.multiregion.MultiregionTable.Kind;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure (no-database) tests of the multi-region check's diff algorithm, reflection-based entity
 * scanning, identifier quoting, and the warn-vs-throw decision.
 */
class MultiregionCheckTest {

    // ---- helpers ---------------------------------------------------------

    private static MultiregionState multiregion(Map<TableName, TableInfo> tables, String... keywords) {
        return new MultiregionState("app", "public", "us-east1",
                List.of("us-east1", "us-west1"), tables, Set.of(keywords));
    }

    private static MultiregionState notMultiregion(List<String> clusterRegions, Map<TableName, TableInfo> tables) {
        return new MultiregionState("app", "public", null, clusterRegions, tables, Set.of());
    }

    private static TableName name(String table) {
        return new TableName("public", table);
    }

    private static MultiregionTable entity(String fqn, String table, Kind kind, String regionColumn) {
        return new MultiregionTable(fqn, null, table, kind, regionColumn);
    }

    // ---- diff: GLOBAL ----------------------------------------------------

    @Test
    void globalOnGlobalTablePasses() {
        var state = multiregion(Map.of(name("widget"), new TableInfo("GLOBAL", Map.of())));
        var report = MultiregionCheck.evaluate(state, List.of(entity("E", "widget", Kind.GLOBAL, null)));
        assertTrue(report.isEmpty());
    }

    @Test
    void globalOnRegionalByRowTableIsContradiction() {
        var state = multiregion(Map.of(name("widget"), new TableInfo("REGIONAL BY ROW", Map.of())));
        var report = MultiregionCheck.evaluate(state, List.of(entity("E", "widget", Kind.GLOBAL, null)));
        assertTrue(report.hasContradictions());
        assertTrue(report.statements().contains("ALTER TABLE public.widget SET LOCALITY GLOBAL;"));
    }

    @Test
    void globalOnOtherLocalityIsPlainProblem() {
        var state = multiregion(Map.of(name("widget"),
                new TableInfo("REGIONAL BY TABLE IN PRIMARY REGION", Map.of())));
        var report = MultiregionCheck.evaluate(state, List.of(entity("E", "widget", Kind.GLOBAL, null)));
        assertFalse(report.isEmpty());
        assertFalse(report.hasContradictions());
    }

    @Test
    void globalOnMissingTableReportsItDoesNotExist() {
        var state = multiregion(Map.of());
        var report = MultiregionCheck.evaluate(state, List.of(entity("E", "widget", Kind.GLOBAL, null)));
        assertTrue(report.problems().get(0).contains("does not exist"));
        assertTrue(report.statements().isEmpty());
    }

    // ---- diff: REGIONAL BY ROW ------------------------------------------

    @Test
    void regionalByRowImplicitColumnPasses() {
        var state = multiregion(Map.of(name("acct"), new TableInfo("REGIONAL BY ROW", Map.of())));
        var report = MultiregionCheck.evaluate(state, List.of(entity("E", "acct", Kind.REGIONAL_BY_ROW, null)));
        assertTrue(report.isEmpty());
    }

    @Test
    void regionalByRowMatchingNamedColumnPasses() {
        var state = multiregion(Map.of(name("acct"),
                new TableInfo("REGIONAL BY ROW AS country", Map.of("country", "crdb_internal_region"))));
        var report = MultiregionCheck.evaluate(state, List.of(entity("E", "acct", Kind.REGIONAL_BY_ROW, "country")));
        assertTrue(report.isEmpty());
    }

    @Test
    void regionalByRowOnGlobalTableIsContradiction() {
        var state = multiregion(Map.of(name("acct"), new TableInfo("GLOBAL", Map.of())));
        var report = MultiregionCheck.evaluate(state, List.of(entity("E", "acct", Kind.REGIONAL_BY_ROW, null)));
        assertTrue(report.hasContradictions());
    }

    @Test
    void regionalByRowNamedColumnWrongTypeIsProblem() {
        var state = multiregion(Map.of(name("acct"),
                new TableInfo("REGIONAL BY TABLE IN PRIMARY REGION", Map.of("country", "STRING"))));
        var report = MultiregionCheck.evaluate(state, List.of(entity("E", "acct", Kind.REGIONAL_BY_ROW, "country")));
        assertFalse(report.hasContradictions());
        assertTrue(report.problems().get(0).contains("crdb_internal_region"));
    }

    @Test
    void regionalByRowNamedColumnMissingIsProblem() {
        var state = multiregion(Map.of(name("acct"),
                new TableInfo("REGIONAL BY TABLE IN PRIMARY REGION", Map.of())));
        var report = MultiregionCheck.evaluate(state, List.of(entity("E", "acct", Kind.REGIONAL_BY_ROW, "country")));
        assertTrue(report.problems().get(0).contains("does not exist"));
    }

    // ---- diff: unannotated & conflicting --------------------------------

    @Test
    void unannotatedEntityOnGlobalTableNeedsAnnotation() {
        var state = multiregion(Map.of(name("widget"), new TableInfo("GLOBAL", Map.of())));
        var report = MultiregionCheck.evaluate(state, List.of(entity("E", "widget", Kind.NONE, null)));
        assertTrue(report.problems().get(0).contains("has no @GlobalTable"));
    }

    @Test
    void unannotatedEntityOnRegionalByRowTableNeedsAnnotation() {
        var state = multiregion(Map.of(name("acct"), new TableInfo("REGIONAL BY ROW", Map.of())));
        var report = MultiregionCheck.evaluate(state, List.of(entity("E", "acct", Kind.NONE, null)));
        assertTrue(report.problems().get(0).contains("has no @RegionalByRow"));
    }

    @Test
    void entityWithBothAnnotationsIsContradiction() {
        var state = multiregion(Map.of(name("widget"), new TableInfo("GLOBAL", Map.of())));
        var report = MultiregionCheck.evaluate(state, List.of(entity("E", "widget", Kind.BOTH, null)));
        assertTrue(report.hasContradictions());
        assertTrue(report.problems().get(0).contains("both @GlobalTable and @RegionalByRow"));
    }

    @Test
    void twoEntitiesOnSameTableWithDifferentAnnotationsIsContradiction() {
        var state = multiregion(Map.of(name("widget"), new TableInfo("GLOBAL", Map.of())));
        var report = MultiregionCheck.evaluate(state, List.of(
                entity("A", "widget", Kind.GLOBAL, null),
                entity("B", "widget", Kind.REGIONAL_BY_ROW, null)));
        assertTrue(report.hasContradictions());
        assertTrue(report.problems().get(0).contains("conflicting annotations"));
    }

    // ---- database not multi-region --------------------------------------

    @Test
    void notMultiregionWithClusterRegionsSuggestsAlterDatabase() {
        var state = notMultiregion(List.of("us-east1", "us-west1"),
                Map.of(name("widget"), new TableInfo(null, Map.of())));
        var report = MultiregionCheck.evaluate(state, List.of(entity("E", "widget", Kind.GLOBAL, null)));
        assertFalse(report.hasContradictions());
        assertTrue(report.problems().get(0).contains("not multi-region"));
        assertTrue(report.statements().contains("ALTER DATABASE app SET PRIMARY REGION \"us-east1\";"));
        assertTrue(report.statements().contains("ALTER DATABASE app ADD REGION \"us-west1\";"));
    }

    @Test
    void notMultiregionWithoutClusterRegionsSaysSo() {
        var state = notMultiregion(List.of(),
                Map.of(name("widget"), new TableInfo(null, Map.of())));
        var report = MultiregionCheck.evaluate(state, List.of(entity("E", "widget", Kind.GLOBAL, null)));
        assertTrue(report.problems().get(0).contains("cluster has no regions"));
        assertFalse(report.statements().stream().anyMatch(s -> s.contains("SET PRIMARY REGION")));
    }

    // ---- keyword quoting -------------------------------------------------

    @Test
    void keywordTableNameIsQuoted() {
        var state = multiregion(Map.of(), "order");
        // table "order" is a reserved keyword; the report must quote it.
        var report = MultiregionCheck.evaluate(state, List.of(entity("E", "order", Kind.GLOBAL, null)));
        assertTrue(report.problems().get(0).contains("public.\"order\""));
    }

    @Test
    void nonKeywordTableNameIsNotQuoted() {
        var state = multiregion(Map.of());
        var report = MultiregionCheck.evaluate(state, List.of(entity("E", "order", Kind.GLOBAL, null)));
        assertTrue(report.problems().get(0).contains("public.order"));
    }

    // ---- reflection scan -------------------------------------------------

    @GlobalTable
    static class Widget {
    }

    @RegionalByRow
    static class Account {
        @CrdbRegion
        String region;
    }

    @GlobalTable(table = "app.\"Catalog\"")
    static class Qualified {
    }

    @RegionalByRow(regionColumn = "country")
    static class NamedColumn {
    }

    @GlobalTable
    @RegionalByRow
    static class Both {
    }

    static class Plain {
    }

    @Test
    void scanReadsGlobalTable() {
        MultiregionTable t = MultiregionCheck.scan(Widget.class).get(0);
        assertEquals(Kind.GLOBAL, t.kind());
        assertEquals("widget", t.table());
        assertNull(t.schema());
        assertNull(t.regionColumn());
    }

    @Test
    void scanReadsRegionalByRowWithCrdbRegionField() {
        MultiregionTable t = MultiregionCheck.scan(Account.class).get(0);
        assertEquals(Kind.REGIONAL_BY_ROW, t.kind());
        assertEquals("account", t.table());
        assertEquals("region", t.regionColumn());
    }

    @Test
    void scanParsesQualifiedAndQuotedTableName() {
        MultiregionTable t = MultiregionCheck.scan(Qualified.class).get(0);
        assertEquals("app", t.schema());
        assertEquals("Catalog", t.table());
    }

    @Test
    void scanUsesExplicitRegionColumn() {
        MultiregionTable t = MultiregionCheck.scan(NamedColumn.class).get(0);
        assertEquals("country", t.regionColumn());
    }

    @Test
    void scanReadsBothAsBoth() {
        assertEquals(Kind.BOTH, MultiregionCheck.scan(Both.class).get(0).kind());
    }

    @Test
    void scanReadsPlainAsNone() {
        MultiregionTable t = MultiregionCheck.scan(Plain.class).get(0);
        assertEquals(Kind.NONE, t.kind());
        assertEquals("plain", t.table());
    }

    // ---- warn-vs-throw decision (teeth) ---------------------------------

    /**
     * Teeth-check: a contradiction MUST throw even in WARN mode. If the
     * {@code || report.hasContradictions()} guard in {@link MultiregionCheck#react} is reverted
     * so only VALIDATE throws, this test fails.
     */
    @Test
    void contradictionThrowsEvenInWarnMode() {
        var report = new MultiregionReport();
        report.contradiction("widget is GLOBAL, not REGIONAL BY ROW");
        assertThrows(IllegalStateException.class, () -> MultiregionCheck.react(report, Mode.WARN, 1));
    }

    @Test
    void validateThrowsOnPlainProblem() {
        var report = new MultiregionReport();
        report.problem("something");
        assertThrows(IllegalStateException.class, () -> MultiregionCheck.react(report, Mode.VALIDATE, 1));
    }

    @Test
    void warnDoesNotThrowOnPlainProblem() {
        var report = new MultiregionReport();
        report.problem("something");
        assertDoesNotThrow(() -> MultiregionCheck.react(report, Mode.WARN, 1));
    }

    @Test
    void emptyReportNeverThrows() {
        assertDoesNotThrow(() -> MultiregionCheck.react(new MultiregionReport(), Mode.VALIDATE, 1));
    }
}
