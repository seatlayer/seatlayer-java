package io.seatlayer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A minimal JSON codec.
 *
 * <p>Deliberately hand-written rather than depending on Jackson or Gson. A server SDK
 * that drags in a JSON library forces its version on every consumer — and in Java that
 * is a real cost, because an application already using a different Jackson version has
 * to resolve the conflict before it can use us at all. The API's payloads are plain
 * objects, arrays, strings, numbers and booleans, so the codec that reads them fits in
 * a few hundred lines and has no opinions to conflict with.
 *
 * <p>Values map to: {@code Map<String,Object>}, {@code List<Object>}, {@code String},
 * {@code Double} or {@code Long}, {@code Boolean}, {@code null}.
 */
final class Json {

    private Json() {
    }

    // ---------- writing ----------

    static String write(Object value) {
        StringBuilder out = new StringBuilder();
        writeValue(value, out);
        return out.toString();
    }

    private static void writeValue(Object value, StringBuilder out) {
        if (value == null) {
            out.append("null");
        } else if (value instanceof String s) {
            writeString(s, out);
        } else if (value instanceof Boolean || value instanceof Integer || value instanceof Long) {
            out.append(value);
        } else if (value instanceof Double || value instanceof Float) {
            double d = ((Number) value).doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d)) {
                throw new IllegalArgumentException("Cannot encode a non-finite number as JSON: " + d);
            }
            // Emit whole doubles without a trailing .0 so ttlMs=900000.0 does not
            // reach an API that expects an integer.
            if (d == Math.rint(d) && Math.abs(d) < 1e15) {
                out.append((long) d);
            } else {
                out.append(d);
            }
        } else if (value instanceof Number n) {
            out.append(n);
        } else if (value instanceof Map<?, ?> map) {
            out.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) {
                    out.append(',');
                }
                first = false;
                writeString(String.valueOf(entry.getKey()), out);
                out.append(':');
                writeValue(entry.getValue(), out);
            }
            out.append('}');
        } else if (value instanceof Iterable<?> items) {
            out.append('[');
            boolean first = true;
            for (Object item : items) {
                if (!first) {
                    out.append(',');
                }
                first = false;
                writeValue(item, out);
            }
            out.append(']');
        } else {
            throw new IllegalArgumentException("Cannot encode as JSON: " + value.getClass().getName());
        }
    }

    private static void writeString(String value, StringBuilder out) {
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                default -> {
                    // Control characters must be escaped; everything else, including
                    // non-ASCII, is emitted as-is because the body is sent as UTF-8.
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        out.append('"');
    }

    // ---------- reading ----------

    static Object read(String json) {
        Parser parser = new Parser(json);
        parser.skipWhitespace();
        Object value = parser.parseValue();
        parser.skipWhitespace();
        if (!parser.atEnd()) {
            throw new JsonException("Trailing content at position " + parser.position);
        }
        return value;
    }

    /** Read a value expected to be an object; anything else is a protocol error. */
    @SuppressWarnings("unchecked")
    static Map<String, Object> readObject(String json) {
        Object value = read(json);
        if (!(value instanceof Map)) {
            throw new JsonException("Expected a JSON object, got " + describe(value));
        }
        return (Map<String, Object>) value;
    }

    private static String describe(Object value) {
        return value == null ? "null" : value.getClass().getSimpleName();
    }

    static final class JsonException extends RuntimeException {
        JsonException(String message) {
            super(message);
        }
    }

    private static final class Parser {
        private final String source;
        private int position;

        Parser(String source) {
            this.source = source;
        }

        boolean atEnd() {
            return position >= source.length();
        }

        void skipWhitespace() {
            while (position < source.length()) {
                char c = source.charAt(position);
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                    position++;
                } else {
                    return;
                }
            }
        }

        Object parseValue() {
            if (atEnd()) {
                throw new JsonException("Unexpected end of input");
            }
            char c = source.charAt(position);
            return switch (c) {
                case '{' -> parseObject();
                case '[' -> parseArray();
                case '"' -> parseString();
                case 't', 'f' -> parseBoolean();
                case 'n' -> parseNull();
                default -> parseNumber();
            };
        }

        private Map<String, Object> parseObject() {
            expect('{');
            Map<String, Object> result = new LinkedHashMap<>();
            skipWhitespace();
            if (peek() == '}') {
                position++;
                return result;
            }
            while (true) {
                skipWhitespace();
                String key = parseString();
                skipWhitespace();
                expect(':');
                skipWhitespace();
                result.put(key, parseValue());
                skipWhitespace();
                char next = peek();
                position++;
                if (next == '}') {
                    return result;
                }
                if (next != ',') {
                    throw new JsonException("Expected ',' or '}' at position " + (position - 1));
                }
            }
        }

        private List<Object> parseArray() {
            expect('[');
            List<Object> result = new ArrayList<>();
            skipWhitespace();
            if (peek() == ']') {
                position++;
                return result;
            }
            while (true) {
                skipWhitespace();
                result.add(parseValue());
                skipWhitespace();
                char next = peek();
                position++;
                if (next == ']') {
                    return result;
                }
                if (next != ',') {
                    throw new JsonException("Expected ',' or ']' at position " + (position - 1));
                }
            }
        }

        private String parseString() {
            expect('"');
            StringBuilder out = new StringBuilder();
            while (true) {
                if (atEnd()) {
                    throw new JsonException("Unterminated string");
                }
                char c = source.charAt(position++);
                if (c == '"') {
                    return out.toString();
                }
                if (c != '\\') {
                    out.append(c);
                    continue;
                }
                if (atEnd()) {
                    throw new JsonException("Unterminated escape");
                }
                char escape = source.charAt(position++);
                switch (escape) {
                    case '"' -> out.append('"');
                    case '\\' -> out.append('\\');
                    case '/' -> out.append('/');
                    case 'n' -> out.append('\n');
                    case 'r' -> out.append('\r');
                    case 't' -> out.append('\t');
                    case 'b' -> out.append('\b');
                    case 'f' -> out.append('\f');
                    case 'u' -> {
                        if (position + 4 > source.length()) {
                            throw new JsonException("Truncated \\u escape");
                        }
                        out.append((char) Integer.parseInt(source.substring(position, position + 4), 16));
                        position += 4;
                    }
                    default -> throw new JsonException("Unknown escape \\" + escape);
                }
            }
        }

        private Boolean parseBoolean() {
            if (source.startsWith("true", position)) {
                position += 4;
                return Boolean.TRUE;
            }
            if (source.startsWith("false", position)) {
                position += 5;
                return Boolean.FALSE;
            }
            throw new JsonException("Invalid literal at position " + position);
        }

        private Object parseNull() {
            if (source.startsWith("null", position)) {
                position += 4;
                return null;
            }
            throw new JsonException("Invalid literal at position " + position);
        }

        private Number parseNumber() {
            int start = position;
            while (position < source.length() && "+-0123456789.eE".indexOf(source.charAt(position)) >= 0) {
                position++;
            }
            String text = source.substring(start, position);
            if (text.isEmpty()) {
                throw new JsonException("Expected a value at position " + start);
            }
            // Integers stay Long so an id or epoch-millis does not come back as 1.0E12.
            if (text.indexOf('.') < 0 && text.indexOf('e') < 0 && text.indexOf('E') < 0) {
                try {
                    return Long.parseLong(text);
                } catch (NumberFormatException ignored) {
                    // Falls through to double for values beyond long range.
                }
            }
            try {
                return Double.parseDouble(text);
            } catch (NumberFormatException error) {
                throw new JsonException("Invalid number \"" + text + "\" at position " + start);
            }
        }

        private char peek() {
            if (atEnd()) {
                throw new JsonException("Unexpected end of input");
            }
            return source.charAt(position);
        }

        private void expect(char expected) {
            if (atEnd() || source.charAt(position) != expected) {
                throw new JsonException("Expected '" + expected + "' at position " + position);
            }
            position++;
        }
    }
}
