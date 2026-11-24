package com.cockroachlabs.crdb.multiregion;

/**
 * How {@link com.cockroachlabs.crdb.Crdb#verifyLocality} reacts to what the
 * multi-region check finds.
 *
 * <p>A <em>contradiction</em> (the annotations and the schema disagree about
 * where rows live) always fails in {@link #WARN} as well as {@link #VALIDATE};
 * only {@link #NONE} lets it pass.
 */
public enum Mode {

    /** No check. */
    NONE,

    /**
     * Log any problems, with SQL that fixes them, and return the report (the
     * default). A contradiction still throws.
     */
    WARN,

    /** Throw on any problem, with SQL that fixes it. */
    VALIDATE
}
