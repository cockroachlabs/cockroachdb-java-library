package com.cockroachlabs.crdb.ai;

/**
 * Helpers for CockroachDB's {@code VECTOR} literal format.
 */
public final class Vectors {

    private Vectors() {
    }

    /**
     * Render a float vector as a CockroachDB vector literal, e.g.
     * {@code [0.1,0.2,0.3]} (square brackets, comma-separated, no spaces). An
     * empty or null array renders as {@code []}.
     */
    public static String toLiteral(float[] vector) {
        if (vector == null || vector.length == 0) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder(vector.length * 8 + 2);
        sb.append('[');
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(trim(vector[i]));
        }
        sb.append(']');
        return sb.toString();
    }

    // Compact, round-trippable float rendering (avoids scientific notation surprises).
    private static String trim(float f) {
        if (f == Math.rint(f) && !Float.isInfinite(f)) {
            return Long.toString((long) f);
        }
        return Float.toString(f);
    }
}
