package io.seatlayer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The JSON codec is hand-written to keep the SDK dependency-free, which means it has to
 * earn that with tests. These cover the cases that actually appear in API payloads and
 * the ones a naive parser gets wrong.
 */
class JsonTest {

    @Test
    @DisplayName("round-trips the shape of a real hold response")
    void roundTripsHoldResponse() {
        String source = """
            {"ok":true,"holdId":"h_9f2c","expiresAt":1754006400000,
             "items":[{"label":"A-1","unitPrice":20,"currency":"GBP","tierId":null}],
             "labels":["A-1"]}""";

        Map<String, Object> decoded = Json.readObject(source);

        assertEquals(Boolean.TRUE, decoded.get("ok"));
        assertEquals("h_9f2c", decoded.get("holdId"));
        // Epoch millis must stay integral — a double would render as 1.7540064E12.
        assertEquals(1754006400000L, decoded.get("expiresAt"));
        assertEquals(List.of("A-1"), decoded.get("labels"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) decoded.get("items");
        assertEquals(20L, items.get(0).get("unitPrice"));
        assertTrue(items.get(0).containsKey("tierId"));
        assertEquals(null, items.get(0).get("tierId"));
    }

    @Test
    @DisplayName("keeps integers integral and decimals decimal")
    void numberTypes() {
        Map<String, Object> decoded = Json.readObject("{\"i\":42,\"d\":19.99,\"e\":1e3,\"neg\":-7}");
        assertInstanceOf(Long.class, decoded.get("i"));
        assertInstanceOf(Double.class, decoded.get("d"));
        assertInstanceOf(Double.class, decoded.get("e"));
        assertEquals(-7L, decoded.get("neg"));
    }

    @Test
    @DisplayName("does not emit a trailing .0 for whole doubles")
    void writesWholeDoublesAsIntegers() {
        // ttlMs=900000.0 reaching an API that expects an integer is a real failure.
        assertEquals("{\"ttlMs\":900000}", Json.write(Map.of("ttlMs", 900000.0)));
        assertEquals("{\"price\":19.99}", Json.write(Map.of("price", 19.99)));
    }

    @Test
    @DisplayName("escapes what must be escaped and passes through what must not")
    void stringEscaping() {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("quote", "say \"hi\"");
        value.put("backslash", "a\\b");
        value.put("newline", "a\nb");
        value.put("control", "a\u0001b");
        value.put("unicode", "Théâtre — 日本");

        String written = Json.write(value);
        assertTrue(written.contains("say \\\"hi\\\""));
        assertTrue(written.contains("a\\\\b"));
        assertTrue(written.contains("a\\nb"));
        assertTrue(written.contains("\\u0001"));
        // Non-ASCII is emitted as-is; the body is sent as UTF-8.
        assertTrue(written.contains("Théâtre — 日本"));

        assertEquals(value, Json.readObject(written));
    }

    @Test
    @DisplayName("decodes escapes, including \\u")
    void decodesEscapes() {
        Map<String, Object> decoded =
                Json.readObject("{\"a\":\"line\\nbreak\",\"b\":\"\\u00e9\",\"c\":\"sl\\/ash\"}");
        assertEquals("line\nbreak", decoded.get("a"));
        assertEquals("é", decoded.get("b"));
        assertEquals("sl/ash", decoded.get("c"));
    }

    @Test
    @DisplayName("handles empty containers and nesting")
    void emptyAndNested() {
        assertEquals(Map.of(), Json.readObject("{}"));
        assertEquals(List.of(), Json.read("[]"));
        Map<String, Object> nested = Json.readObject("{\"a\":{\"b\":[{\"c\":1}]},\"d\":[]}");
        assertEquals(List.of(), nested.get("d"));
    }

    @Test
    @DisplayName("tolerates whitespace anywhere it is legal")
    void whitespace() {
        Map<String, Object> decoded = Json.readObject("  {\n \"a\" : [ 1 , 2 ] \t}\n");
        assertEquals(List.of(1L, 2L), decoded.get("a"));
    }

    @Test
    @DisplayName("rejects malformed input rather than guessing")
    void rejectsMalformed() {
        assertThrows(Json.JsonException.class, () -> Json.read("{\"a\":1"));
        assertThrows(Json.JsonException.class, () -> Json.read("{\"a\" 1}"));
        assertThrows(Json.JsonException.class, () -> Json.read("{}{}"));
        assertThrows(Json.JsonException.class, () -> Json.read("\"unterminated"));
        assertThrows(Json.JsonException.class, () -> Json.read("nope"));
    }

    @Test
    @DisplayName("refuses to encode a non-finite number")
    void rejectsNonFinite() {
        // Silently emitting NaN would produce a body the API cannot parse.
        assertThrows(IllegalArgumentException.class, () -> Json.write(Map.of("x", Double.NaN)));
        assertThrows(IllegalArgumentException.class, () -> Json.write(Map.of("x", Double.POSITIVE_INFINITY)));
    }

    @Test
    @DisplayName("preserves key order so request bodies are stable")
    void preservesKeyOrder() {
        // Stable ordering matters: an Idempotency-Key is matched against the body.
        assertEquals("{\"b\":1,\"a\":2}", Json.write(new LinkedHashMap<>(Map.of()) {{
            put("b", 1);
            put("a", 2);
        }}));
    }

    @Test
    @DisplayName("readObject rejects a non-object top level")
    void readObjectRejectsArray() {
        assertThrows(Json.JsonException.class, () -> Json.readObject("[1,2]"));
    }
}
