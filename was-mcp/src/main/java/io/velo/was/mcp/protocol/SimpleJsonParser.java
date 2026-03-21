package io.velo.was.mcp.protocol;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal recursive-descent JSON parser that produces a plain Java object graph:
 * <ul>
 *   <li>JSON object → {@code Map<String, Object>}</li>
 *   <li>JSON array  → {@code List<Object>}</li>
 *   <li>JSON string → {@code String}</li>
 *   <li>JSON number → {@code Long} or {@code Double}</li>
 *   <li>JSON boolean → {@code Boolean}</li>
 *   <li>JSON null   → {@code null}</li>
 * </ul>
 *
 * <p>Not thread-safe — create a new instance per call or use the static helper.
 */
public final class SimpleJsonParser {

    private final String src;
    private int pos;

    private SimpleJsonParser(String src) {
        this.src = src;
        this.pos = 0;
    }

    /** Parse {@code json} and return the root value. */
    public static Object parse(String json) {
        if (json == null || json.isBlank()) {
            throw new JsonParseException("Empty JSON input");
        }
        SimpleJsonParser p = new SimpleJsonParser(json.trim());
        Object result = p.value();
        p.skipWhitespace();
        if (p.pos != p.src.length()) {
            throw new JsonParseException("Trailing content at position " + p.pos);
        }
        return result;
    }

    // ── Value dispatch ───────────────────────────────────────────────────────

    private Object value() {
        skipWhitespace();
        if (pos >= src.length()) {
            throw new JsonParseException("Unexpected end of input");
        }
        char c = src.charAt(pos);
        return switch (c) {
            case '{' -> object();
            case '[' -> array();
            case '"' -> string();
            case 't' -> literal("true", Boolean.TRUE);
            case 'f' -> literal("false", Boolean.FALSE);
            case 'n' -> literal("null", null);
            default -> number();
        };
    }

    // ── Object ───────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private Map<String, Object> object() {
        expect('{');
        Map<String, Object> map = new LinkedHashMap<>();
        skipWhitespace();
        if (peek() == '}') { advance(); return map; }
        while (true) {
            skipWhitespace();
            String key = string();
            skipWhitespace();
            expect(':');
            Object val = value();
            map.put(key, val);
            skipWhitespace();
            char sep = peek();
            if (sep == '}') { advance(); break; }
            if (sep == ',') { advance(); continue; }
            throw new JsonParseException("Expected ',' or '}' at position " + pos);
        }
        return map;
    }

    // ── Array ────────────────────────────────────────────────────────────────

    private List<Object> array() {
        expect('[');
        List<Object> list = new ArrayList<>();
        skipWhitespace();
        if (peek() == ']') { advance(); return list; }
        while (true) {
            list.add(value());
            skipWhitespace();
            char sep = peek();
            if (sep == ']') { advance(); break; }
            if (sep == ',') { advance(); continue; }
            throw new JsonParseException("Expected ',' or ']' at position " + pos);
        }
        return list;
    }

    // ── String ───────────────────────────────────────────────────────────────

    private String string() {
        expect('"');
        StringBuilder sb = new StringBuilder();
        while (pos < src.length()) {
            char c = src.charAt(pos);
            if (c == '"') { pos++; return sb.toString(); }
            if (c == '\\') {
                pos++;
                if (pos >= src.length()) break;
                char esc = src.charAt(pos++);
                switch (esc) {
                    case '"'  -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    case '/'  -> sb.append('/');
                    case 'b'  -> sb.append('\b');
                    case 'f'  -> sb.append('\f');
                    case 'n'  -> sb.append('\n');
                    case 'r'  -> sb.append('\r');
                    case 't'  -> sb.append('\t');
                    case 'u'  -> {
                        if (pos + 4 > src.length()) throw new JsonParseException("Incomplete \\u escape");
                        String hex = src.substring(pos, pos + 4);
                        sb.append((char) Integer.parseInt(hex, 16));
                        pos += 4;
                    }
                    default -> sb.append(esc);
                }
            } else {
                sb.append(c);
                pos++;
            }
        }
        throw new JsonParseException("Unterminated string");
    }

    // ── Number ───────────────────────────────────────────────────────────────

    private Number number() {
        int start = pos;
        boolean isFloat = false;
        if (pos < src.length() && src.charAt(pos) == '-') pos++;
        while (pos < src.length() && Character.isDigit(src.charAt(pos))) pos++;
        if (pos < src.length() && src.charAt(pos) == '.') {
            isFloat = true;
            pos++;
            while (pos < src.length() && Character.isDigit(src.charAt(pos))) pos++;
        }
        if (pos < src.length() && (src.charAt(pos) == 'e' || src.charAt(pos) == 'E')) {
            isFloat = true;
            pos++;
            if (pos < src.length() && (src.charAt(pos) == '+' || src.charAt(pos) == '-')) pos++;
            while (pos < src.length() && Character.isDigit(src.charAt(pos))) pos++;
        }
        String raw = src.substring(start, pos);
        if (raw.isEmpty()) throw new JsonParseException("Invalid number at position " + start);
        try {
            return isFloat ? Double.parseDouble(raw) : Long.parseLong(raw);
        } catch (NumberFormatException e) {
            throw new JsonParseException("Malformed number '" + raw + "' at position " + start);
        }
    }

    // ── Literal ──────────────────────────────────────────────────────────────

    private Object literal(String expected, Object value) {
        if (src.startsWith(expected, pos)) {
            pos += expected.length();
            return value;
        }
        throw new JsonParseException("Expected '" + expected + "' at position " + pos);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private void skipWhitespace() {
        while (pos < src.length() && Character.isWhitespace(src.charAt(pos))) pos++;
    }

    private char peek() {
        if (pos >= src.length()) throw new JsonParseException("Unexpected end of input");
        return src.charAt(pos);
    }

    private void advance() { pos++; }

    private void expect(char c) {
        skipWhitespace();
        if (pos >= src.length() || src.charAt(pos) != c) {
            throw new JsonParseException("Expected '" + c + "' at position " + pos
                    + " but found '" + (pos < src.length() ? src.charAt(pos) : "EOF") + "'");
        }
        pos++;
    }
}
