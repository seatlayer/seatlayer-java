package io.seatlayer;

import static io.seatlayer.SeatLayerHttpClient.body;
import static io.seatlayer.SeatLayerHttpClient.encode;

import java.util.List;
import java.util.Map;

/**
 * Holds, booking, blocking, availability.
 *
 * <p>Two complete flows, both first-class:
 *
 * <pre>
 *   browser holds → retrieveHold for authoritative pricing → charge → book(holdId)
 *   backend books labels directly — box office, phone sales, comps
 * </pre>
 *
 * <p>Never price from what the browser tells you. {@link #retrieveHold} is the
 * authoritative answer, which is why it is a separate call.
 */
public final class Inventory {

    private final SeatLayerHttpClient http;

    Inventory(SeatLayerHttpClient http) {
        this.http = http;
    }

    private String path(String eventKey, String suffix) {
        return "/v1/events/" + encode(eventKey) + suffix;
    }

    public Map<String, Object> hold(String eventKey, List<String> labels) {
        return http.post(path(eventKey, "/hold"), body("labels", labels));
    }

    public Map<String, Object> hold(String eventKey, List<String> labels, Long ttlMs, String idempotencyKey) {
        return http.post(path(eventKey, "/hold"), body("labels", labels, "ttlMs", ttlMs), idempotencyKey);
    }

    /**
     * Ask us to pick the best free objects and hold them.
     *
     * <p>The picker is the one the buyer widget uses, so a phone order and a web order
     * get the same answer for the same inventory. A qty above the server cap is clamped,
     * not rejected.
     */
    public Map<String, Object> holdBestAvailable(String eventKey, int qty) {
        return http.post(path(eventKey, "/best-available"), body("qty", qty));
    }

    public Map<String, Object> holdBestAvailable(
            String eventKey, int qty, String categoryKey, String zoneId, Long ttlMs, String idempotencyKey) {
        return http.post(
                path(eventKey, "/best-available"),
                body("qty", qty, "categoryKey", categoryKey, "zoneId", zoneId, "ttlMs", ttlMs),
                idempotencyKey);
    }

    /**
     * Pick and book in one call — the box-office shape.
     *
     * <p>Prefer this over hold-then-book when payment is already taken: a failure between
     * two calls would strand inventory until the TTL expired.
     */
    public Map<String, Object> bookBestAvailable(String eventKey, int qty, String bookingRef) {
        return http.post(path(eventKey, "/best-available-book"), body("qty", qty, "bookingRef", bookingRef));
    }

    public Map<String, Object> bookBestAvailable(
            String eventKey, int qty, String bookingRef, String categoryKey, String zoneId, String idempotencyKey) {
        return http.post(
                path(eventKey, "/best-available-book"),
                body("qty", qty, "bookingRef", bookingRef, "categoryKey", categoryKey, "zoneId", zoneId),
                idempotencyKey);
    }

    /**
     * Push an active hold's expiry out by a fresh window before it lapses.
     *
     * <p>Use this rather than release-and-re-hold when an order takes longer than the
     * checkout window — invoiced sales, a phone order on hold. Releasing first hands the
     * seats to whoever is racing for them in between. A hold that is gone, expired, or at
     * its renewal cap answers 409 {@code cannot_extend}.
     */
    public Map<String, Object> extendHold(String eventKey, String holdId) {
        return http.post(path(eventKey, "/extend"), body("holdId", holdId));
    }

    public Map<String, Object> extendHold(String eventKey, String holdId, Long ttlMs) {
        return http.post(path(eventKey, "/extend"), body("holdId", holdId, "ttlMs", ttlMs));
    }

    /** Authoritative items and prices. Charge from this, not the browser. */
    public Map<String, Object> retrieveHold(String eventKey, String holdId) {
        return http.get(path(eventKey, "/holds/" + encode(holdId)));
    }

    public Map<String, Object> release(String eventKey, List<String> labels, String holdId) {
        return http.post(path(eventKey, "/release"), body("labels", labels, "holdId", holdId));
    }

    public Map<String, Object> book(String eventKey, String holdId, String bookingRef) {
        return http.post(path(eventKey, "/book"), body("holdId", holdId, "bookingRef", bookingRef));
    }

    public Map<String, Object> bookLabels(String eventKey, List<String> labels, String bookingRef) {
        return http.post(path(eventKey, "/book"), body("labels", labels, "bookingRef", bookingRef));
    }

    public Map<String, Object> boxOfficeBook(String eventKey, List<String> labels, String bookingRef) {
        return http.post(path(eventKey, "/box-book"), body("labels", labels, "bookingRef", bookingRef));
    }

    /** Reverse a booking. Requires a key with cancel authority. */
    public Map<String, Object> unbook(String eventKey, List<String> labels) {
        return http.post(path(eventKey, "/unbook"), body("labels", labels));
    }

    /** Hold inventory back from sale (house seats, production holds). */
    public Map<String, Object> block(String eventKey, List<String> labels) {
        return http.post(path(eventKey, "/block"), body("labels", labels));
    }

    public Map<String, Object> unblock(String eventKey, List<String> labels) {
        return http.post(path(eventKey, "/unblock"), body("labels", labels));
    }

    public Map<String, Object> unblockAll(String eventKey) {
        return http.post(path(eventKey, "/unblock-all"));
    }

    public Map<String, Object> retrieveAvailability(String eventKey) {
        return http.get(path(eventKey, "/availability"));
    }

    public Map<String, Object> updateAvailability(String eventKey, Map<String, Object> fields) {
        return http.post(path(eventKey, "/availability"), fields);
    }
}
