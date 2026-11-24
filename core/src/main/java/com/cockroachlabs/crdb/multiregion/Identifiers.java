package com.cockroachlabs.crdb.multiregion;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Parses and quotes SQL identifiers the way CockroachDB treats them: unquoted names fold to
 * lower case, quoted names are kept verbatim.
 */
final class Identifiers {

    private static final Pattern PLAIN = Pattern.compile("[a-z_][a-z0-9_$]*");

    private Identifiers() {
    }

    /**
     * Splits a possibly qualified, possibly quoted name such as {@code app."Orders"} into its
     * parts, as CockroachDB stores them.
     */
    static List<String> parse(String rendered) {
        List<String> parts = new ArrayList<>();
        StringBuilder part = new StringBuilder();
        boolean inQuotes = false;
        boolean quoted = false;
        for (int i = 0; i < rendered.length(); i++) {
            char c = rendered.charAt(i);
            if (inQuotes) {
                if (c != '"') {
                    part.append(c);
                } else if (i + 1 < rendered.length() && rendered.charAt(i + 1) == '"') {
                    part.append('"');
                    i++;
                } else {
                    inQuotes = false;
                }
            } else if (c == '"') {
                inQuotes = true;
                quoted = true;
            } else if (c == '.') {
                parts.add(finish(part, quoted));
                part.setLength(0);
                quoted = false;
            } else {
                part.append(c);
            }
        }
        parts.add(finish(part, quoted));
        return parts;
    }

    private static String finish(StringBuilder part, boolean quoted) {
        String name = part.toString();
        return quoted ? name : name.trim().toLowerCase(Locale.ROOT);
    }

    /** Quotes {@code name} when it can't be written as a plain identifier. */
    static String quote(String name, Set<String> keywords) {
        if (PLAIN.matcher(name).matches() && !keywords.contains(name)) {
            return name;
        }
        return '"' + name.replace("\"", "\"\"") + '"';
    }
}
