package io.seatlayer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Webhook verification — the piece integrations most often get wrong. */
class WebhookTest {

    private static final String SECRET = "whsec_test";

    private static String sign(String payload) {
        return sign(payload, SECRET);
    }

    private static String sign(String payload, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return "sha256=" + hex;
        } catch (Exception error) {
            throw new IllegalStateException(error);
        }
    }

    @Test
    @DisplayName("accepts a correctly signed delivery")
    void acceptsSigned() {
        String payload = "{\"type\":\"booking.created\",\"occurrenceId\":\"occ_1\"}";
        var event = Webhook.verify(payload, sign(payload), SECRET);
        assertEquals("booking.created", event.get("type"));
    }

    @Test
    @DisplayName("accepts raw bytes, which is what a servlet hands you")
    void acceptsBytes() {
        String payload = "{\"ok\":true}";
        var event = Webhook.verify(payload.getBytes(StandardCharsets.UTF_8), sign(payload), SECRET);
        assertEquals(Boolean.TRUE, event.get("ok"));
    }

    @Test
    @DisplayName("rejects a body that was re-serialised rather than passed through raw")
    void rejectsReserialised() {
        // The classic integration bug: re-encoding the decoded body reorders keys and
        // the bytes no longer match what was signed.
        String original = "{\"a\":1,\"b\":2}";
        String reserialised = Json.write(Json.readObject("{\"b\":2,\"a\":1}"));
        assertThrows(
                WebhookVerificationException.class,
                () -> Webhook.verify(reserialised, sign(original), SECRET));
    }

    @Test
    @DisplayName("rejects a signature made with the wrong secret")
    void rejectsWrongSecret() {
        String payload = "{\"ok\":true}";
        var error = assertThrows(
                WebhookVerificationException.class,
                () -> Webhook.verify(payload, sign(payload, "whsec_other"), SECRET));
        assertTrue(error.getMessage().contains("did not match"));
    }

    @Test
    @DisplayName("rejects a missing header rather than trusting the body")
    void rejectsMissingHeader() {
        assertThrows(WebhookVerificationException.class, () -> Webhook.verify("{}", null, SECRET));
    }

    @Test
    @DisplayName("rejects an unknown signature scheme")
    void rejectsUnknownScheme() {
        var error = assertThrows(
                WebhookVerificationException.class, () -> Webhook.verify("{}", "md5=abc", SECRET));
        assertTrue(error.getMessage().contains("Unsupported signature format"));
    }

    @Test
    @DisplayName("rejects a truncated signature without throwing on length mismatch")
    void rejectsTruncated() {
        String payload = "{\"ok\":true}";
        String truncated = sign(payload).substring(0, 20);
        assertThrows(WebhookVerificationException.class, () -> Webhook.verify(payload, truncated, SECRET));
    }

    @Test
    @DisplayName("requires a secret")
    void requiresSecret() {
        assertThrows(WebhookVerificationException.class, () -> Webhook.verify("{}", sign("{}"), ""));
    }

    @Test
    @DisplayName("reports a verified-but-unparseable body distinctly")
    void reportsUnparseable() {
        String payload = "not json";
        var error = assertThrows(
                WebhookVerificationException.class, () -> Webhook.verify(payload, sign(payload), SECRET));
        assertTrue(error.getMessage().contains("not valid JSON"));
    }

    @Test
    @DisplayName("verifies a body containing non-ASCII, byte-for-byte")
    void verifiesNonAscii() {
        // A venue named "Théâtre" must verify — this is where a String/byte mismatch
        // in the HMAC would show up.
        String payload = "{\"venue\":\"Théâtre du Châtelet — 日本\"}";
        var event = Webhook.verify(payload, sign(payload), SECRET);
        assertEquals("Théâtre du Châtelet — 日本", event.get("venue"));
    }
}
