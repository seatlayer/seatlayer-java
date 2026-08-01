package io.seatlayer;

import static io.seatlayer.SeatLayerHttpClient.body;
import static io.seatlayer.SeatLayerHttpClient.encode;

import java.util.Map;

/** Workspaces isolate one tenant's charts and events from another's. */
public final class Workspaces {

    private final SeatLayerHttpClient http;

    Workspaces(SeatLayerHttpClient http) {
        this.http = http;
    }

    public Map<String, Object> list() {
        return http.get("/v1/workspaces");
    }

    public Map<String, Object> create(String name) {
        return http.post("/v1/workspaces", body("name", name));
    }

    public Map<String, Object> create(String name, String externalRef, String idempotencyKey) {
        return http.post("/v1/workspaces", body("name", name, "externalRef", externalRef), idempotencyKey);
    }

    public Map<String, Object> retrieve(String workspaceId) {
        return http.get("/v1/workspaces/" + encode(workspaceId));
    }

    /**
     * Rename, re-reference, or disable a workspace.
     *
     * <p>The organisation's default workspace cannot be disabled — the API answers 409
     * {@code default_workspace_required}. Promote another one first.
     */
    public Map<String, Object> update(String workspaceId, Map<String, Object> fields) {
        return http.patch("/v1/workspaces/" + encode(workspaceId), fields);
    }
}
