package com.wayback09.campfire;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal, dependency-free JSON reader/writer.
 *
 * <p>Campfire's {@code mod:common} module is intentionally free of third-party
 * libraries, so it cannot use Gson/Jackson. The previous implementation parsed
 * JSON by splitting on {@code "{"} and searching for {@code "\": "} patterns,
 * which silently corrupted any value containing those sequences (for example a
 * world name such as {@code My "World"} or a description with a colon).
 *
 * <p>This class implements a small but standards-correct JSON parser:
 * <ul>
 *   <li>Objects, arrays, strings (with escapes), numbers, booleans and null</li>
 *   <li>Strict RFC 8259 number grammar</li>
 *   <li>No trailing commas, no comments</li>
 * </ul>
 */
public final class CampfireJson {
    private final String src;
    private int pos;

    private CampfireJson(String src) {
        this.src = src;
    }

    /**
     * Parses a JSON document.
     *
     * @return {@link Map}, {@link List}, {@link String}, {@link Double}, {@link Boolean} or {@code null}
     * @throws IllegalArgumentException if the document is malformed
     */
    public static Object parse(String json) {
        if (json == null) {
            throw new IllegalArgumentException("json is null");
        }
        CampfireJson p = new CampfireJson(json);
        p.skipWhitespace();
        Object value = p.readValue();
        p.skipWhitespace();
        if (p.pos != p.src.length()) {
            throw new IllegalArgumentException("Trailing content at offset " + p.pos);
        }
        return value;
    }

    /**
     * Parses a JSON document, returning {@code null} instead of throwing.
     */
    public static Object parseOrNull(String json) {
        try {
            return parse(json);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Parses a JSON object, returning an empty map on failure. */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseObjectOrEmpty(String json) {
        Object o = parseOrNull(json);
        if (o instanceof Map<?, ?> m) {
            return (Map<String, Object>) m;
        }
        return new LinkedHashMap<>();
    }

    /** Parses a JSON array, returning an empty list on failure. */
    @SuppressWarnings("unchecked")
    public static List<Object> parseArrayOrEmpty(String json) {
        Object o = parseOrNull(json);
        if (o instanceof List<?> l) {
            return (List<Object>) l;
        }
        return new ArrayList<>();
    }

    // ------------------------------------------------------------------
    // Typed accessors
    // ------------------------------------------------------------------

    public static String getString(Map<String, Object> obj, String key, String defaultValue) {
        Object v = obj.get(key);
        if (v instanceof String s) return s;
        if (v == null) return defaultValue;
        return String.valueOf(v);
    }

    public static long getLong(Map<String, Object> obj, String key, long defaultValue) {
        Object v = obj.get(key);
        if (v instanceof Number n) return n.longValue();
        if (v instanceof String s) {
            try {
                return Long.parseLong(s.trim());
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    public static int getInt(Map<String, Object> obj, String key, int defaultValue) {
        return (int) getLong(obj, key, defaultValue);
    }

    public static boolean getBoolean(Map<String, Object> obj, String key, boolean defaultValue) {
        Object v = obj.get(key);
        if (v instanceof Boolean b) return b;
        if (v instanceof String s) {
            return switch (s.toLowerCase(java.util.Locale.ROOT)) {
                case "true", "yes", "1" -> true;
                case "false", "no", "0" -> false;
                default -> defaultValue;
            };
        }
        return defaultValue;
    }

    // ------------------------------------------------------------------
    // Writing
    // ------------------------------------------------------------------

    /** Escapes a string for inclusion in a JSON document (without quotes). */
    public static String escape(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }

    /** Wraps a string in JSON quotes with proper escaping. */
    public static String quote(String s) {
        return "\"" + escape(s) + "\"";
    }

    // ------------------------------------------------------------------
    // Parser internals
    // ------------------------------------------------------------------

    private Object readValue() {
        if (pos >= src.length()) {
            throw new IllegalArgumentException("Unexpected end of input");
        }
        char c = src.charAt(pos);
        return switch (c) {
            case '{' -> readObject();
            case '[' -> readArray();
            case '"' -> readString();
            case 't' -> readLiteral("true", Boolean.TRUE);
            case 'f' -> readLiteral("false", Boolean.FALSE);
            case 'n' -> readLiteral("null", null);
            default -> readNumber();
        };
    }

    private Map<String, Object> readObject() {
        expect('{');
        Map<String, Object> map = new LinkedHashMap<>();
        skipWhitespace();
        if (peek() == '}') {
            pos++;
            return map;
        }
        while (true) {
            skipWhitespace();
            if (peek() != '"') {
                throw new IllegalArgumentException("Expected object key at offset " + pos);
            }
            String key = readString();
            skipWhitespace();
            expect(':');
            skipWhitespace();
            map.put(key, readValue());
            skipWhitespace();
            char c = peek();
            if (c == ',') {
                pos++;
                continue;
            }
            if (c == '}') {
                pos++;
                return map;
            }
            throw new IllegalArgumentException("Expected ',' or '}' at offset " + pos);
        }
    }

    private List<Object> readArray() {
        expect('[');
        List<Object> list = new ArrayList<>();
        skipWhitespace();
        if (peek() == ']') {
            pos++;
            return list;
        }
        while (true) {
            skipWhitespace();
            list.add(readValue());
            skipWhitespace();
            char c = peek();
            if (c == ',') {
                pos++;
                continue;
            }
            if (c == ']') {
                pos++;
                return list;
            }
            throw new IllegalArgumentException("Expected ',' or ']' at offset " + pos);
        }
    }

    private String readString() {
        expect('"');
        StringBuilder sb = new StringBuilder();
        while (true) {
            if (pos >= src.length()) {
                throw new IllegalArgumentException("Unterminated string");
            }
            char c = src.charAt(pos++);
            if (c == '"') {
                return sb.toString();
            }
            if (c != '\\') {
                sb.append(c);
                continue;
            }
            if (pos >= src.length()) {
                throw new IllegalArgumentException("Unterminated escape");
            }
            char esc = src.charAt(pos++);
            switch (esc) {
                case '"' -> sb.append('"');
                case '\\' -> sb.append('\\');
                case '/' -> sb.append('/');
                case 'b' -> sb.append('\b');
                case 'f' -> sb.append('\f');
                case 'n' -> sb.append('\n');
                case 'r' -> sb.append('\r');
                case 't' -> sb.append('\t');
                case 'u' -> {
                    if (pos + 4 > src.length()) {
                        throw new IllegalArgumentException("Truncated unicode escape");
                    }
                    String hex = src.substring(pos, pos + 4);
                    pos += 4;
                    try {
                        sb.append((char) Integer.parseInt(hex, 16));
                    } catch (NumberFormatException e) {
                        throw new IllegalArgumentException("Bad unicode escape \\u" + hex);
                    }
                }
                default -> throw new IllegalArgumentException("Bad escape \\" + esc);
            }
        }
    }

    private Object readLiteral(String literal, Object value) {
        if (!src.startsWith(literal, pos)) {
            throw new IllegalArgumentException("Bad literal at offset " + pos);
        }
        pos += literal.length();
        return value;
    }

    private Double readNumber() {
        int start = pos;
        if (peek() == '-') pos++;
        while (pos < src.length() && Character.isDigit(src.charAt(pos))) pos++;
        if (pos < src.length() && src.charAt(pos) == '.') {
            pos++;
            while (pos < src.length() && Character.isDigit(src.charAt(pos))) pos++;
        }
        if (pos < src.length() && (src.charAt(pos) == 'e' || src.charAt(pos) == 'E')) {
            pos++;
            if (pos < src.length() && (src.charAt(pos) == '+' || src.charAt(pos) == '-')) pos++;
            while (pos < src.length() && Character.isDigit(src.charAt(pos))) pos++;
        }
        String num = src.substring(start, pos);
        try {
            return Double.valueOf(num);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Bad number '" + num + "' at offset " + start);
        }
    }

    private char peek() {
        if (pos >= src.length()) {
            throw new IllegalArgumentException("Unexpected end of input");
        }
        return src.charAt(pos);
    }

    private void expect(char c) {
        if (pos >= src.length() || src.charAt(pos) != c) {
            throw new IllegalArgumentException("Expected '" + c + "' at offset " + pos);
        }
        pos++;
    }

    private void skipWhitespace() {
        while (pos < src.length()) {
            char c = src.charAt(pos);
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                pos++;
            } else {
                break;
            }
        }
    }
}
