package io.seatlayer;

import static io.seatlayer.SeatLayerHttpClient.body;
import static io.seatlayer.SeatLayerHttpClient.encode;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Private allocations, reporting, and origin-bound buyer access. */
public final class Channels {

    private final SeatLayerHttpClient http;

    Channels(SeatLayerHttpClient http) {
        this.http = http;
    }

    private String path(String eventKey, String suffix) {
        return "/v1/events/" + encode(eventKey) + "/channels" + suffix;
    }

    public Map<String, Object> list(String eventKey) {
        return http.get(path(eventKey, ""));
    }

    public Map<String, Object> list(String eventKey, boolean includeArchived) {
        return http.get(path(eventKey, ""), includeArchived ? body("includeArchived", "1") : Map.of());
    }

    public Map<String, Object> create(
            String eventKey,
            String name,
            String color,
            String marker,
            String externalRef,
            String accessIntent,
            String reason,
            String idempotencyKey) {
        return http.post(
                path(eventKey, ""),
                body(
                        "name", name,
                        "color", color,
                        "marker", marker,
                        "externalRef", externalRef,
                        "accessIntent", accessIntent,
                        "reason", reason),
                idempotencyKey);
    }

    public Map<String, Object> update(
            String eventKey,
            String channelId,
            String name,
            String accessIntent,
            Boolean acknowledgeLiveAccess,
            String reason) {
        return http.patch(
                path(eventKey, "/" + encode(channelId)),
                body(
                        "name", name,
                        "accessIntent", accessIntent,
                        "acknowledgeLiveAccess", acknowledgeLiveAccess,
                        "reason", reason));
    }

    public Map<String, Object> updateAssignments(
            String eventKey,
            List<String> labels,
            long assignmentVersion,
            String targetChannelId,
            String reason,
            String idempotencyKey) {
        Map<String, Object> request = new LinkedHashMap<>(
                body("labels", labels, "assignmentVersion", assignmentVersion, "reason", reason));
        request.put("targetChannelId", targetChannelId);
        return http.post(path(eventKey, "/assignments"), request, idempotencyKey);
    }

    public Map<String, Object> listAllocation(String eventKey, String afterLabel, Integer limit) {
        return http.get(path(eventKey, "/allocation"), body("afterLabel", afterLabel, "limit", limit));
    }

    public Map<String, Object> retrieveAccessPreview(
            String eventKey, List<String> channelIds, Boolean includePublic) {
        return http.get(
                path(eventKey, "/preview"),
                body(
                        "channelIds", channelIds == null ? null : String.join(",", channelIds),
                        "includePublic", Boolean.TRUE.equals(includePublic) ? "1" : null));
    }

    public Map<String, Object> retrieveReport(String eventKey) {
        return http.get(path(eventKey, "/report"));
    }

    public Map<String, Object> pause(String eventKey, String channelId, String reason) {
        return http.post(path(eventKey, "/" + encode(channelId) + "/pause"), body("reason", reason));
    }

    public Map<String, Object> unpause(String eventKey, String channelId, String reason) {
        return http.post(path(eventKey, "/" + encode(channelId) + "/unpause"), body("reason", reason));
    }

    public Map<String, Object> archive(
            String eventKey, String channelId, String destination, String reason) {
        Map<String, Object> request = new LinkedHashMap<>(body("reason", reason));
        request.put("destination", destination);
        return http.post(
                path(eventKey, "/" + encode(channelId) + "/archive"),
                request);
    }

    /** Mints a short-lived, origin-bound buyer access token. */
    public Map<String, Object> createBuyerAccessSession(
            String eventKey,
            boolean includePublic,
            String allowedOrigin,
            List<String> channelIds,
            Integer expiresInSeconds,
            Integer maxQuantity,
            String buyerRef,
            String partnerRef,
            String clientRequestId,
            String idempotencyKey) {
        return http.post(
                "/v1/events/" + encode(eventKey) + "/buyer-access-sessions",
                body(
                        "channelIds", channelIds,
                        "includePublic", includePublic,
                        "allowedOrigin", allowedOrigin,
                        "expiresInSeconds", expiresInSeconds,
                        "maxQuantity", maxQuantity,
                        "buyerRef", buyerRef,
                        "partnerRef", partnerRef,
                        "clientRequestId", clientRequestId),
                idempotencyKey);
    }

    public Map<String, Object> listBuyerAccessSessions(String eventKey, Integer limit) {
        return http.get(
                "/v1/events/" + encode(eventKey) + "/buyer-access-sessions",
                body("limit", limit));
    }

    public Map<String, Object> revokeBuyerAccessSession(String eventKey, String sessionId) {
        return http.delete(
                "/v1/events/" + encode(eventKey) + "/buyer-access-sessions/" + encode(sessionId));
    }

    public Map<String, Object> createAccessLink(
            String eventKey,
            String channelId,
            String label,
            Boolean includePublic,
            Long expiresAt,
            Integer maxRedemptions,
            Integer maxQuantity,
            Integer sessionTtlSeconds,
            String reason) {
        return http.post(
                accessLinkPath(eventKey, channelId, ""),
                body(
                        "label", label,
                        "includePublic", includePublic,
                        "expiresAt", expiresAt,
                        "maxRedemptions", maxRedemptions,
                        "maxQuantity", maxQuantity,
                        "sessionTtlSeconds", sessionTtlSeconds,
                        "reason", reason));
    }

    public Map<String, Object> listAccessLinks(String eventKey, String channelId) {
        return http.get(accessLinkPath(eventKey, channelId, ""));
    }

    public Map<String, Object> rotateAccessLink(
            String eventKey, String channelId, String linkId, boolean endActiveSessions, String reason) {
        return http.post(
                accessLinkPath(eventKey, channelId, "/" + encode(linkId) + "/rotate"),
                body("endActiveSessions", endActiveSessions, "reason", reason));
    }

    public Map<String, Object> revokeAccessLink(
            String eventKey,
            String channelId,
            String linkId,
            boolean endActiveSessions,
            String reason) {
        return http.delete(
                accessLinkPath(eventKey, channelId, "/" + encode(linkId)),
                body("endActiveSessions", endActiveSessions ? "1" : null, "reason", reason));
    }

    private String accessLinkPath(String eventKey, String channelId, String suffix) {
        return path(eventKey, "/" + encode(channelId) + "/access-links" + suffix);
    }
}
