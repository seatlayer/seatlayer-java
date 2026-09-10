package io.seatlayer;

import static io.seatlayer.SeatLayerHttpClient.body;
import static io.seatlayer.SeatLayerHttpClient.encode;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Event lifecycle, metadata, reports. */
public final class Events {

    private final SeatLayerHttpClient http;

    Events(SeatLayerHttpClient http) {
        this.http = http;
    }

    /**
     * One page of events.
     *
     * <p>Live availability counts cost one round-trip per event server-side. They are on
     * by default because most callers of a single page want them; turn them off when
     * paging a whole catalogue, where you almost certainly do not.
     */
    public Page<Map<String, Object>> list(EventListOptions options) {
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("workspaceId", options.workspaceId());
        query.put("externalRef", options.externalRef());
        query.put("limit", options.limit());
        query.put("cursor", options.cursor());
        if (!options.counts()) {
            query.put("counts", "0");
        }
        return Page.of(http.get("/v1/events", query), "events");
    }

    public Page<Map<String, Object>> list() {
        return list(EventListOptions.builder().build());
    }

    /**
     * Every event, paging transparently. Counts default off here — you are walking the
     * whole list, so per-event availability is rarely what you want and always what it
     * costs.
     */
    public Iterable<Map<String, Object>> listAll(EventListOptions options) {
        return Page.paginate(cursor -> list(options.withCursor(cursor)));
    }

    public Iterable<Map<String, Object>> listAll() {
        return listAll(EventListOptions.builder().counts(false).build());
    }

    public Map<String, Object> create(String chartId) {
        return http.postWithHeaderReplay("/v1/events", body("chartId", chartId));
    }

    public Map<String, Object> create(String chartId, String name) {
        return http.postWithHeaderReplay("/v1/events", body("chartId", chartId, "name", name));
    }

    public Map<String, Object> create(String chartId, String name, EventHostingRegion region) {
        return http.postWithHeaderReplay(
                "/v1/events", body("chartId", chartId, "name", name, "region", region.value()));
    }

    /** Creates an event with the full public metadata request shape. */
    public Map<String, Object> create(Map<String, Object> params) {
        return http.postWithHeaderReplay("/v1/events", params);
    }

    public Map<String, Object> create(Map<String, Object> params, String idempotencyKey) {
        return http.postWithHeaderReplay("/v1/events", params, idempotencyKey);
    }

    public Map<String, Object> retrieve(String eventKey) {
        return http.get("/v1/events/" + encode(eventKey));
    }

    /** Reads the Event's exact immutable configuration binding and audit history. */
    public Map<String, Object> retrieveConfigurationBinding(String eventKey) {
        return http.get("/v1/events/" + encode(eventKey) + "/event-configuration");
    }

    /**
     * Binds an exact published configuration version, or passes {@code null} to detach.
     *
     * <p>This compare-and-set mutation remains single-attempt because the public operation
     * does not promise exact response replay.
     */
    public Map<String, Object> updateConfigurationBinding(
            String eventKey, long expectedRevision, Map<String, ?> configuration) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("expectedRevision", expectedRevision);
        // Null is meaningful here: it detaches the existing binding and must not
        // be dropped by the optional-field body helper.
        request.put("configuration", configuration);
        return http.put(
                "/v1/events/" + encode(eventKey) + "/event-configuration",
                request);
    }

    public Map<String, Object> update(String eventKey, Map<String, Object> fields) {
        return http.patch("/v1/events/" + encode(eventKey), fields);
    }

    public Map<String, Object> delete(String eventKey) {
        return http.delete("/v1/events/" + encode(eventKey));
    }

    /** Upload raw PNG, JPEG, or WebP poster bytes (maximum 5 MiB). */
    public Map<String, Object> updatePoster(String eventKey, byte[] bytes, String contentType) {
        return http.putBinary("/v1/events/" + encode(eventKey) + "/poster", bytes, contentType);
    }

    public Map<String, Object> deletePoster(String eventKey) {
        return http.delete("/v1/events/" + encode(eventKey) + "/poster");
    }

    /** Move a live event onto the latest published version of its chart. */
    public Map<String, Object> updateChart(String eventKey) {
        return updateChart(eventKey, null, null);
    }

    public Map<String, Object> updateChart(
            String eventKey, Boolean acknowledgeDroppedAssignments, String reason) {
        return http.post(
                "/v1/events/" + encode(eventKey) + "/update-chart",
                body(
                        "acknowledgeDroppedAssignments", acknowledgeDroppedAssignments,
                        "reason", reason));
    }

    /** Stop buyer sales. Existing holds keep their TTL. */
    public Map<String, Object> close(String eventKey) {
        return http.post("/v1/events/" + encode(eventKey) + "/close");
    }

    public Map<String, Object> reopen(String eventKey) {
        return http.post("/v1/events/" + encode(eventKey) + "/reopen");
    }

    public Map<String, Object> archive(String eventKey) {
        return http.post("/v1/events/" + encode(eventKey) + "/archive");
    }

    public Map<String, Object> retrieveHoldTtl(String eventKey) {
        return http.get("/v1/events/" + encode(eventKey) + "/hold-ttl");
    }

    public Map<String, Object> updateHoldTtl(String eventKey, Long holdTtlMs) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("holdTtlMs", holdTtlMs);
        return http.post("/v1/events/" + encode(eventKey) + "/hold-ttl", request);
    }

    /** Lists the event's ticket releases, including current quota consumption. */
    public Map<String, Object> listTicketReleases(String eventKey) {
        return http.get("/v1/events/" + encode(eventKey) + "/releases");
    }

    /**
     * Replaces the event's complete, ordered ticket-release list.
     *
     * <p>Each map is a release input. Do not send response-only fields such as
     * {@code position}, {@code soldOutAt}, {@code consumed}, or {@code remaining}; the server
     * derives those from the ordered replacement and live inventory.
     */
    public Map<String, Object> updateTicketReleases(
            String eventKey, List<? extends Map<String, ?>> releases) {
        return http.put(
                "/v1/events/" + encode(eventKey) + "/releases",
                body("releases", releases));
    }

    /** Ends one ticket release immediately. This mutation is intentionally single-attempt. */
    public Map<String, Object> closeTicketRelease(String eventKey, String releaseId) {
        return http.post(
                "/v1/events/" + encode(eventKey) + "/releases/" + encode(releaseId) + "/close");
    }

    public Map<String, Object> retrieveReport(String eventKey) {
        return http.get("/v1/events/" + encode(eventKey) + "/report");
    }

    public Map<String, Object> retrieveLog(String eventKey) {
        return http.get("/v1/events/" + encode(eventKey) + "/log");
    }

    public Map<String, Object> retrieveLog(String eventKey, Integer limit, Long before) {
        return http.get(
                "/v1/events/" + encode(eventKey) + "/log",
                body("limit", limit, "before", before));
    }
}
