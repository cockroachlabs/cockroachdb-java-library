package com.cockroachlabs.crdb.ai;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Minimal JSON support for flat {@code Map<String,String>} metadata, so the AI
 * module stays dependency-free (no Jackson). Values are always written as JSON
 * strings; on read, scalar values (string/number/boolean/null) are coerced to
 * their string form.
 */
final class Json {

    private Json() {
    }

    /** Encode a flat string map as a JSON object. Null map → {@code {}}. */
    static String encodeObject(Map<String, String> map) {
        if (map == null || map.isEmpty()) {
            return "{}";
        }
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        boolean first = true;
        for (Map.Entry<String, String> e : map.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            writeString(sb, e.getKey());
            sb.append(':');
            if (e.getValue() == null) {
                sb.append("null");
            } else {
                writeString(sb, e.getValue());
            }
        }
        sb.append('}');
        return sb.toString();
    }

    /**
     * Parse a flat JSON object into a string map. Scalar values are coerced to
     * their string form; nested objects/arrays are stored verbatim as their raw
     * JSON text. Best-effort: a value that cannot be parsed yields a
     * {@code {"_raw": <text>}} map.
     */
    static Map<String, String> parseObject(String json) {
        Map<String, String> out = new LinkedHashMap<>();
        if (json == null) {
            return out;
        }
        try {
            new Parser(json).parseInto(out);
        } catch (RuntimeException e) {
            out.clear();
            out.put("_raw", json);
        }
        return out;
    }

    private static void writeString(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
    }

    /** A tiny recursive-descent parser for flat JSON objects. */
    private static final class Parser {
        private final String s;
        private int i;

        Parser(String s) {
            this.s = s;
        }

        void parseInto(Map<String, String> out) {
            ws();
            expect('{');
            ws();
            if (peek() == '}') {
                i++;
                return;
            }
            while (true) {
                ws();
                String key = parseString();
                ws();
                expect(':');
                ws();
                String value = parseValue();
                out.put(key, value);
                ws();
                char c = next();
                if (c == '}') {
                    return;
                }
                if (c != ',') {
                    throw new IllegalStateException("expected ',' or '}'");
                }
            }
        }

        private String parseValue() {
            char c = peek();
            if (c == '"') {
                return parseString();
            }
            if (c == '{' || c == '[') {
                return parseRaw(c, c == '{' ? '}' : ']');
            }
            // literal: number, true, false, null
            int start = i;
            while (i < s.length() && ",}] \t\n\r".indexOf(s.charAt(i)) < 0) {
                i++;
            }
            String token = s.substring(start, i).trim();
            return "null".equals(token) ? null : token;
        }

        private String parseRaw(char open, char close) {
            int start = i;
            int depth = 0;
            boolean inStr = false;
            while (i < s.length()) {
                char c = s.charAt(i++);
                if (inStr) {
                    if (c == '\\') {
                        i++;
                    } else if (c == '"') {
                        inStr = false;
                    }
                } else if (c == '"') {
                    inStr = true;
                } else if (c == open) {
                    depth++;
                } else if (c == close) {
                    depth--;
                    if (depth == 0) {
                        return s.substring(start, i);
                    }
                }
            }
            throw new IllegalStateException("unterminated " + open);
        }

        private String parseString() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (true) {
                char c = next();
                if (c == '"') {
                    return sb.toString();
                }
                if (c == '\\') {
                    char e = next();
                    switch (e) {
                        case '"' -> sb.append('"');
                        case '\\' -> sb.append('\\');
                        case '/' -> sb.append('/');
                        case 'n' -> sb.append('\n');
                        case 'r' -> sb.append('\r');
                        case 't' -> sb.append('\t');
                        case 'b' -> sb.append('\b');
                        case 'f' -> sb.append('\f');
                        case 'u' -> {
                            String hex = s.substring(i, i + 4);
                            i += 4;
                            sb.append((char) Integer.parseInt(hex, 16));
                        }
                        default -> throw new IllegalStateException("bad escape: \\" + e);
                    }
                } else {
                    sb.append(c);
                }
            }
        }

        private void ws() {
            while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
                i++;
            }
        }

        private char peek() {
            if (i >= s.length()) {
                throw new IllegalStateException("unexpected end of input");
            }
            return s.charAt(i);
        }

        private char next() {
            return s.charAt(i++);
        }

        private void expect(char c) {
            if (next() != c) {
                throw new IllegalStateException("expected '" + c + "'");
            }
        }
    }
}
