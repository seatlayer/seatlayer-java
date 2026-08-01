package io.seatlayer;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Webhook signature verification.
 *
 * <p>The most security-sensitive thing an integrator writes by hand, and the two classic
 * mistakes are both easy to make and silent:
 *
 * <ol>
 *   <li>verifying against a re-serialised body, which changes bytes and fails — or worse,
 *       gets "fixed" by skipping verification entirely;
 *   <li>comparing signatures with {@code equals}, which leaks the expected value through
 *       timing.
 * </ol>
 *
 * <p>So the SDK does it, takes the RAW body, and compares in constant time.
 */
public final class Webhook {

    private Webhook() {
    }

    /**
     * Verify a delivery and return its decoded payload.
     *
     * <p>{@code payload} must be the raw request body. In a servlet that is the bytes
     * read from {@code request.getInputStream()}; in Spring, declare the handler
     * parameter as {@code @RequestBody byte[]} — never a parsed object re-serialised.
     *
     * <p>NOTE ON REPLAY: deliveries are signed over the body, which carries an {@code at}
     * timestamp — but nothing enforces a freshness window, so a captured delivery stays
     * valid indefinitely. Replay protection is yours: every event carries an
     * {@code occurrenceId}, and the correct pattern is to record processed ids and ignore
     * repeats. Do not skip this.
     *
     * @throws WebhookVerificationException when the delivery is not from SeatLayer
     */
    public static Map<String, Object> verify(byte[] payload, String signature, String secret) {
        if (secret == null || secret.isBlank()) {
            throw new WebhookVerificationException("A webhook signing secret is required.");
        }
        if (signature == null || signature.isBlank()) {
            throw new WebhookVerificationException("Missing X-SeatLayer-Signature header.");
        }

        int separator = signature.indexOf('=');
        if (separator < 0
                || !"sha256".equals(signature.substring(0, separator))
                || separator == signature.length() - 1) {
            throw new WebhookVerificationException(
                    "Unsupported signature format \"" + signature + "\"; expected \"sha256=<hex>\".");
        }
        String provided = signature.substring(separator + 1);

        String expected = hmacSha256Hex(secret, payload);
        // MessageDigest.isEqual is constant time and handles a length mismatch without
        // leaking which of the two failures occurred.
        if (!MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8), provided.getBytes(StandardCharsets.UTF_8))) {
            throw new WebhookVerificationException("Webhook signature did not match.");
        }

        try {
            return Json.readObject(new String(payload, StandardCharsets.UTF_8));
        } catch (RuntimeException error) {
            throw new WebhookVerificationException(
                    "Signature verified but the body is not valid JSON: " + error.getMessage());
        }
    }

    /** Convenience for callers holding the body as a String. */
    public static Map<String, Object> verify(String payload, String signature, String secret) {
        return verify(payload.getBytes(StandardCharsets.UTF_8), signature, secret);
    }

    private static String hmacSha256Hex(String secret, byte[] payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(payload);
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(Character.forDigit((b >> 4) & 0xf, 16)).append(Character.forDigit(b & 0xf, 16));
            }
            return hex.toString();
        } catch (java.security.GeneralSecurityException error) {
            throw new IllegalStateException("HmacSHA256 unavailable on this JVM", error);
        }
    }
}
