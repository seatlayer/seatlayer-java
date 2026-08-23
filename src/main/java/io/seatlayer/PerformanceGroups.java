package io.seatlayer;

import static io.seatlayer.SeatLayerHttpClient.body;
import static io.seatlayer.SeatLayerHttpClient.encode;

import java.util.List;
import java.util.Map;

/**
 * Fixed multi-performance runs, trusted hold inspection, and host-authorized booking.
 *
 * <p>This is a secret-key resource. Pass only the one-time token returned from
 * {@link #createBuyerAccessSession(String, String, boolean)} to PerformanceGroupPicker in a browser.
 */
public final class PerformanceGroups {

    private final SeatLayerHttpClient http;

    PerformanceGroups(SeatLayerHttpClient http) {
        this.http = http;
    }

    private static String path(String performanceGroupKey, String suffix) {
        return "/v1/performance-groups/" + encode(performanceGroupKey) + suffix;
    }

    public Map<String, Object> list() {
        return http.get("/v1/performance-groups");
    }

    public Map<String, Object> list(
            String workspaceId, String externalRef, String state, Integer limit, String cursor) {
        return http.get(
                "/v1/performance-groups",
                body(
                        "workspaceId", workspaceId,
                        "externalRef", externalRef,
                        "state", state,
                        "limit", limit,
                        "cursor", cursor));
    }

    /** Create a draft run with exact idempotency replay. */
    public Map<String, Object> create(String name, List<String> eventKeys) {
        return create(name, eventKeys, null, null);
    }

    public Map<String, Object> create(
            String name, List<String> eventKeys, String externalRef, String idempotencyKey) {
        return http.postWithHeaderReplay(
                "/v1/performance-groups",
                body("name", name, "eventKeys", eventKeys, "externalRef", externalRef),
                idempotencyKey);
    }

    public Map<String, Object> retrieve(String performanceGroupKey) {
        return http.get(path(performanceGroupKey, ""));
    }

    /** Delete a draft only; activated runs remain available for audit. */
    public void delete(String performanceGroupKey) {
        http.delete(path(performanceGroupKey, ""));
    }

    /** Start activation. Poll retrieveLifecycle when the returned operation is not terminal. */
    public Map<String, Object> activate(String performanceGroupKey, int expectedRevision) {
        return http.post(path(performanceGroupKey, "/activate"), body("expectedRevision", expectedRevision));
    }

    /** Stop new group sales. Poll retrieveLifecycle until the close becomes terminal. */
    public Map<String, Object> close(String performanceGroupKey, int expectedRevision) {
        return http.post(path(performanceGroupKey, "/close"), body("expectedRevision", expectedRevision));
    }

    public Map<String, Object> retrieveLifecycle(String performanceGroupKey, String operationId) {
        return http.get(path(performanceGroupKey, "/lifecycle/" + encode(operationId)));
    }

    /**
     * Reveal one origin-bound browser bearer. This call is deliberately single-attempt.
     */
    public Map<String, Object> createBuyerAccessSession(
            String performanceGroupKey, String allowedOrigin, boolean includePublic) {
        return createBuyerAccessSession(
                performanceGroupKey, allowedOrigin, includePublic, null, null, null, null, null);
    }

    public Map<String, Object> createBuyerAccessSession(
            String performanceGroupKey,
            String allowedOrigin,
            boolean includePublic,
            Map<String, List<String>> channelIdsByEvent,
            Integer expiresInSeconds,
            Integer maxQuantity,
            String buyerRef,
            String partnerRef) {
        return http.post(
                path(performanceGroupKey, "/buyer-access-sessions"),
                body(
                        "allowedOrigin", allowedOrigin,
                        "includePublic", includePublic,
                        "channelIdsByEvent", channelIdsByEvent,
                        "expiresInSeconds", expiresInSeconds,
                        "maxQuantity", maxQuantity,
                        "buyerRef", buyerRef,
                        "partnerRef", partnerRef));
    }

    public Map<String, Object> listBuyerAccessSessions(String performanceGroupKey) {
        return listBuyerAccessSessions(performanceGroupKey, null);
    }

    public Map<String, Object> listBuyerAccessSessions(String performanceGroupKey, Integer limit) {
        return http.get(path(performanceGroupKey, "/buyer-access-sessions"), body("limit", limit));
    }

    public Map<String, Object> revokeBuyerAccessSession(String performanceGroupKey, String sessionId) {
        return http.delete(path(performanceGroupKey, "/buyer-access-sessions/" + encode(sessionId)));
    }

    public Map<String, Object> retrieveHold(String performanceGroupKey, String operationId) {
        return http.get(path(performanceGroupKey, "/holds/" + encode(operationId)));
    }

    /** Reuse both stable IDs and poll retrieveBooking while the returned booking is pending. */
    public Map<String, Object> bookHold(
            String performanceGroupKey, String operationId, String bookActionId, String bookingRef) {
        return http.post(
                path(performanceGroupKey, "/holds/" + encode(operationId) + "/book"),
                body("bookActionId", bookActionId, "bookingRef", bookingRef));
    }

    public Map<String, Object> retrieveBooking(String performanceGroupKey, String actionId) {
        return http.get(path(performanceGroupKey, "/bookings/" + encode(actionId)));
    }
}
