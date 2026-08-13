package io.seatlayer;

import static io.seatlayer.SeatLayerHttpClient.body;
import static io.seatlayer.SeatLayerHttpClient.encode;

import java.util.List;
import java.util.Map;

/**
 * Short-lived, origin-bound browser tokens.
 *
 * <p>The governing rule: the SDK mints tokens, widgets consume them. Your secret key
 * never reaches a browser.
 */
public final class Sessions {

    /** Capabilities a manage-session token can carry. */
    public static final List<String> CAPABILITIES =
            List.of(
                    "event:view",
                    "event:block",
                    "event:cancel",
                    "event:reports",
                    "event:channels:view",
                    "event:channels:manage",
                    "event:orders:read",
                    "event:refund",
                    "event:tickets:send",
                    "event:door:view",
                    "event:door:checkin",
                    "event:boxoffice");

    private final SeatLayerHttpClient http;

    Sessions(SeatLayerHttpClient http) {
        this.http = http;
    }

    /**
     * Mint a manage-session token for the control room.
     *
     * <p>{@code capabilities} is required here even though the API defaults omission to
     * {@code event:view}. Making the grant explicit keeps browser authority reviewable
     * and prevents future server defaults from changing client intent.
     */
    public Map<String, Object> createManageSession(
            String eventKey, String allowedOrigin, List<String> capabilities) {
        return createManageSession(eventKey, allowedOrigin, capabilities, null);
    }

    public Map<String, Object> createManageSession(
            String eventKey, String allowedOrigin, List<String> capabilities, Integer expiresInSeconds) {
        return createManageSession(eventKey, allowedOrigin, capabilities, expiresInSeconds, null);
    }

    public Map<String, Object> createManageSession(
            String eventKey,
            String allowedOrigin,
            List<String> capabilities,
            Integer expiresInSeconds,
            String workspaceId) {
        if (capabilities == null || capabilities.isEmpty()) {
            throw new IllegalArgumentException(
                    "capabilities is required: pass the smallest explicit set the page needs, e.g. "
                            + "List.of(\"event:view\").");
        }
        return http.post(
                "/v1/events/" + encode(eventKey) + "/manage-sessions",
                body(
                        "allowedOrigin", allowedOrigin,
                        "capabilities", capabilities,
                        "expiresInSeconds", expiresInSeconds,
                        "workspaceId", workspaceId));
    }

    public Map<String, Object> revokeManageSession(String eventKey, String sessionId) {
        return http.delete("/v1/events/" + encode(eventKey) + "/manage-sessions/" + encode(sessionId));
    }

    /**
     * Mint a designer token so an organiser can edit a chart inside your own UI. Requires
     * a chart id that already exists — create or copy one first.
     */
    public Map<String, Object> createDesignerSession(
            String workspaceId, String chartId, String allowedOrigin) {
        return createDesignerSession(workspaceId, chartId, allowedOrigin, null, null, null);
    }

    public Map<String, Object> createDesignerSession(
            String workspaceId,
            String chartId,
            String allowedOrigin,
            String authority,
            String mode,
            Integer expiresInSeconds) {
        return createDesignerSession(
                workspaceId, chartId, allowedOrigin, authority, mode, expiresInSeconds,
                null, null, null);
    }

    public Map<String, Object> createDesignerSession(
            String workspaceId,
            String chartId,
            String allowedOrigin,
            String authority,
            String mode,
            Integer expiresInSeconds,
            Boolean canPublish,
            Map<String, Boolean> safeModeOptions,
            Map<String, Object> features) {
        return http.post(
                "/v1/designer/sessions",
                body(
                        "workspaceId", workspaceId,
                        "chartId", chartId,
                        "allowedOrigin", allowedOrigin,
                        "authority", authority,
                        "mode", mode,
                        "expiresInSeconds", expiresInSeconds,
                        "canPublish", canPublish,
                        "safeModeOptions", safeModeOptions,
                        "features", features));
    }

    public Map<String, Object> revokeDesignerSession(String sessionId) {
        return http.delete("/v1/designer/sessions/" + encode(sessionId));
    }
}
