package io.seatlayer;

import static io.seatlayer.SeatLayerHttpClient.encode;

import java.util.Map;

/** Published catalog templates that can be materialized as workspace chart drafts. */
public final class Templates {

    private final SeatLayerHttpClient http;

    Templates(SeatLayerHttpClient http) {
        this.http = http;
    }

    /**
     * Instantiates a published template as a draft in the caller's workspace.
     *
     * <p>The API requires a JSON object even when no overrides are needed, so this sends
     * {@code {}}. It uses the server's header-replay contract and therefore retries with
     * one stable idempotency key.
     */
    public Map<String, Object> instantiateTemplate(String templateId) {
        return instantiateTemplate(templateId, Map.of());
    }

    /** Instantiates with optional {@code name}, {@code workspaceId}, or version-pinning overrides. */
    public Map<String, Object> instantiateTemplate(String templateId, Map<String, Object> params) {
        return http.postWithHeaderReplay(
                "/v1/templates/" + encode(templateId) + "/instantiate",
                params == null ? Map.of() : params);
    }

    /** Instantiates with a caller-owned idempotency key for exact server replay. */
    public Map<String, Object> instantiateTemplate(
            String templateId, Map<String, Object> params, String idempotencyKey) {
        return http.postWithHeaderReplay(
                "/v1/templates/" + encode(templateId) + "/instantiate",
                params == null ? Map.of() : params,
                idempotencyKey);
    }
}
