package com.cockroachlabs.crdb.multiregion;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The problems the multi-region check found, and SQL that fixes them. A contradiction (an
 * annotation that names one locality while the table has the other, or annotations that disagree
 * with each other) is a problem that throws even in {@link Mode#WARN}.
 *
 * <p>Returned by {@link com.cockroachlabs.crdb.Crdb#verifyLocality} so a caller can inspect what
 * was found. When the check throws (validate mode, or any contradiction), the thrown
 * {@link IllegalStateException}'s message is this report's {@link #format(Mode)}.
 */
public final class MultiregionReport {

    static final String CONTRADICTION = "Contradiction: ";

    /**
     * Who produced the report, by fully qualified name, so a developer can type it into an IDE
     * and find the class behind the message.
     */
    static final String SOURCE = MultiregionCheck.class.getName();

    static final String ENTRY_POINT = "Crdb.verifyLocality";

    static final String OVERVIEW = "https://docs.cockroachlabs.com/docs/stable/multiregion-overview";

    private final List<String> problems = new ArrayList<>();

    private final List<String> statements = new ArrayList<>();

    private int contradictions;

    MultiregionReport() {
    }

    void problem(String problem) {
        problems.add(problem);
    }

    void contradiction(String problem) {
        problems.add(CONTRADICTION + problem);
        contradictions++;
    }

    void statement(String statement) {
        statements.add(statement);
    }

    /** The problems found, each a human-readable line; contradictions are prefixed {@code Contradiction:}. */
    public List<String> problems() {
        return List.copyOf(problems);
    }

    /** The suggested fix-up SQL, in the order it should be reviewed and applied. */
    public List<String> statements() {
        return List.copyOf(statements);
    }

    /** Whether the check found no problems. */
    public boolean isEmpty() {
        return problems.isEmpty();
    }

    /** Whether any problem is a contradiction (which throws even in {@link Mode#WARN}). */
    public boolean hasContradictions() {
        return contradictions > 0;
    }

    /**
     * The report as one message. Its first line names the class that produced it and the
     * mode the check ran in.
     */
    public String format(Mode mode) {
        StringBuilder out = new StringBuilder("CockroachDB multiregion check (")
                .append(SOURCE).append(", ").append(ENTRY_POINT).append(" mode=")
                .append(mode.name().toLowerCase(Locale.ROOT)).append(") found ")
                .append(problems.size())
                .append(problems.size() == 1 ? " problem:" : " problems:");
        for (String problem : problems) {
            out.append("\n  - ").append(problem.replace("\n", "\n    "));
        }
        if (contradictions > 0) {
            out.append("\n\nContradictions throw in every mode but none: the annotations and the schema")
                    .append(" disagree about where rows live, so one of them is wrong.");
        }
        if (!statements.isEmpty()) {
            out.append("\n\nSuggested SQL (review, then add to a migration; or alter the annotations):");
            for (String statement : statements) {
                out.append("\n  ").append(statement);
            }
        }
        out.append("\n\nSee also: ").append(OVERVIEW);
        return out.toString();
    }
}
