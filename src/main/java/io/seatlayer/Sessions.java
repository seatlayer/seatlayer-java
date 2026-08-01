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

    /** The four capabilities a manage-session token can carry. */
    public static final List<String> CAPABILITIES =
            List.of("event:view", "event:block", "event:cancel", "event:reports");

    private final SeatLayerHttpClient http;

    Sessions(SeatLayerHttpClient http) {
        this.http = http;
    }

    /**
     * Mint a manage-session token for the control room.
     *
     * <p>{@code capabilities} is required here even though the API defaults it. That
     * default grants all four — including {@code event:cancel}, which un-books paid
     * inventory. Granting the ability to reverse sales by forgetting an argument is not
     * a default worth inheriting.
     */
    public Map<String, Object> createManageSession(
            String eventKey, String allowedOrigin, List<String> capabilities) {
        return createManageSession(eventKey, allowedOrigin, capabilities, null);
    }

    public Map<String, Object> createManageSession(
            String eventKey, String allowedOrigin, List<String> capabilities, Integer expiresInSeconds) {
        if (capabilities == null || capabilities.isEmpty()) {
            throw new IllegalArgumentException(
                    "capabilities is required: pass the smallest set the page needs, e.g. "
                            + "List.of(\"event:view\"). Omitting it server-side grants event:cancel, "
                            + "which can reverse paid bookings.");
        }
        return http.post(
                "/v1/events/" + encode(eventKey) + "/manage-sessions",
                body(
                        "allowedOrigin", allowedOrigin,
                        "capabilities", capabilities,
                        "expiresInSeconds", expiresInSeconds));
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
        return http.post(
                "/v1/designer/sessions",
                body(
                        "workspaceId", workspaceId,
                        "chartId", chartId,
                        "allowedOrigin", allowedOrigin,
                        "authority", authority,
                        "mode", mode,
                        "expiresInSeconds", expiresInSeconds));
    }

    public Map<String, Object> revokeDesignerSession(String sessionId) {
        return http.delete("/v1/designer/sessions/" + encode(sessionId));
    }
}
