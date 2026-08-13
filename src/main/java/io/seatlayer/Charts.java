package io.seatlayer;

import static io.seatlayer.SeatLayerHttpClient.body;
import static io.seatlayer.SeatLayerHttpClient.encode;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Seat-map definitions that events are created from.
 *
 * <p>Even when organisers draw their own venues in the embedded Designer you need this:
 * {@code createDesignerSession} requires a chart id that already exists, so the usual
 * platform flow is copy a template here, then hand over a Designer session for it.
 */
public final class Charts {

    private final SeatLayerHttpClient http;

    Charts(SeatLayerHttpClient http) {
        this.http = http;
    }

    /** One page of charts. Pass the previous page's cursor to continue. */
    public Page<Map<String, Object>> list(ChartListOptions options) {
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("workspaceId", options.workspaceId());
        query.put("externalRef", options.externalRef());
        query.put("limit", options.limit());
        query.put("cursor", options.cursor());
        if (options.archived()) {
            query.put("archived", "1");
        }
        return Page.of(http.get("/v1/charts", query), "charts");
    }

    public Page<Map<String, Object>> list() {
        return list(ChartListOptions.builder().build());
    }

    /**
     * Every chart, paging transparently.
     *
     * <pre>{@code for (var chart : seatlayer.charts().listAll()) { … }}</pre>
     */
    public Iterable<Map<String, Object>> listAll(ChartListOptions options) {
        return Page.paginate(cursor -> list(options.withCursor(cursor)));
    }

    public Iterable<Map<String, Object>> listAll() {
        return listAll(ChartListOptions.builder().build());
    }

    public Map<String, Object> create(String name) {
        return http.postWithHeaderReplay("/v1/charts", body("name", name));
    }

    public Map<String, Object> create(
            String name, Map<String, Object> doc, String externalRef, String workspaceId, String idempotencyKey) {
        return http.postWithHeaderReplay(
                "/v1/charts",
                body("name", name, "doc", doc, "externalRef", externalRef, "workspaceId", workspaceId),
                idempotencyKey);
    }

    public Map<String, Object> retrieve(String chartId) {
        return http.get("/v1/charts/" + encode(chartId));
    }

    /**
     * Replace a chart document.
     *
     * <p>{@code expectedUpdatedAt} is required for optimistic concurrency and is not
     * optional here either: without it two concurrent writers silently overwrite each
     * other, and a seat map is exactly the document where that loses work. Read it from
     * {@link #retrieve} immediately before writing.
     *
     * <p>The Designer is the authoring surface. Use this for bulk programmatic edits and
     * migrations, not for drawing.
     */
    public Map<String, Object> update(String chartId, Map<String, Object> doc, long expectedUpdatedAt) {
        return http.put(
                "/v1/charts/" + encode(chartId), body("doc", doc, "expectedUpdatedAt", expectedUpdatedAt));
    }

    /** Updates name, issues, or externalRef without replacing the chart document. */
    public Map<String, Object> update(String chartId, Map<String, Object> fields) {
        return http.put("/v1/charts/" + encode(chartId), fields);
    }

    public Map<String, Object> delete(String chartId) {
        return http.delete("/v1/charts/" + encode(chartId));
    }

    /** Copy a chart — the usual way to provision a venue from a template. */
    public Map<String, Object> copy(String chartId) {
        return http.postWithHeaderReplay("/v1/charts/" + encode(chartId) + "/duplicate");
    }

    public Map<String, Object> copy(
            String chartId,
            String name,
            String externalRef,
            String workspaceId,
            String idempotencyKey) {
        return http.postWithHeaderReplay(
                "/v1/charts/" + encode(chartId) + "/duplicate",
                body(
                        "name", name,
                        "externalRef", externalRef,
                        "workspaceId", workspaceId),
                idempotencyKey);
    }

    public Map<String, Object> archive(String chartId) {
        return http.post("/v1/charts/" + encode(chartId) + "/archive");
    }

    public Map<String, Object> unarchive(String chartId) {
        return http.post("/v1/charts/" + encode(chartId) + "/unarchive");
    }

    /** Publish the draft. Events can only be created from a published chart. */
    public Map<String, Object> publish(String chartId) {
        return http.post("/v1/charts/" + encode(chartId) + "/publish");
    }
}
