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
        query.put("seriesKey", options.seriesKey());
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

    /**
     * Opens a paused event's sales again. A date its season cancelled never opens again:
     * {@code 409 season_date_cancelled}.
     *
     * @param eventKey the event key
     * @return the decoded response
     */
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

    /**
     * Retrieves who takes payment for the event, and what blocks a change.
     *
     * @param eventKey the event key
     * @return the decoded response
     */
    public Map<String, Object> retrieveEventBuyerCheckout(String eventKey) {
        return http.get("/v1/events/" + encode(eventKey) + "/buyer-checkout");
    }

    /**
     * Chooses SeatLayer checkout or your own checkout for the event.
     *
     * <p>Single attempt; the {@code expectedRevision} compare-and-set makes a repeat safe to
     * reconcile.
     *
     * @param eventKey the event key
     * @param params the request body: {@code buyerCheckout} and {@code expectedRevision}
     * @return the decoded response
     */
    public Map<String, Object> replaceEventBuyerCheckout(String eventKey, Map<String, Object> params) {
        return http.put("/v1/events/" + encode(eventKey) + "/buyer-checkout", params);
    }

    /**
     * Retrieves the event's payment gateway and the gateways it could use.
     *
     * @param eventKey the event key
     * @return the decoded response
     */
    public Map<String, Object> retrieveEventPaymentGateway(String eventKey) {
        return retrieveEventPaymentGateway(eventKey, null);
    }

    /**
     * Retrieves the event's payment gateway and the gateways it could use.
     *
     * @param eventKey the event key
     * @param currency check the options against this currency instead of the event's, or
     *     {@code null}
     * @return the decoded response
     */
    public Map<String, Object> retrieveEventPaymentGateway(String eventKey, String currency) {
        return http.get(
                "/v1/events/" + encode(eventKey) + "/payment-gateway", body("currency", currency));
    }

    /**
     * Replaces the event's payment gateway, sending no request body.
     *
     * <p>Single attempt, never retried automatically.
     *
     * @param eventKey the event key
     * @return the decoded response
     */
    public Map<String, Object> replaceEventPaymentGateway(String eventKey) {
        return replaceEventPaymentGateway(eventKey, null);
    }

    /**
     * Assigns one of the organization's connected payment gateways to the event.
     *
     * <p>Single attempt, never retried automatically.
     *
     * @param eventKey the event key
     * @param params the optional request body ({@code connectionId}, {@code provider}), or
     *     {@code null}
     * @return the decoded response
     */
    public Map<String, Object> replaceEventPaymentGateway(String eventKey, Map<String, Object> params) {
        return http.put("/v1/events/" + encode(eventKey) + "/payment-gateway", params);
    }

    /**
     * Retrieves what the event still needs before its next sales step.
     *
     * @param eventKey the event key
     * @return the decoded response
     */
    public Map<String, Object> retrieveEventSalesReadiness(String eventKey) {
        return http.get("/v1/events/" + encode(eventKey) + "/sales/readiness");
    }

    /**
     * Records a test order as the event's sales preflight.
     *
     * <p>Single attempt, never retried automatically.
     *
     * @param eventKey the event key
     * @param params the request body: {@code orderId}
     * @return the decoded response
     */
    public Map<String, Object> preflightEventSales(String eventKey, Map<String, Object> params) {
        return http.post("/v1/events/" + encode(eventKey) + "/sales/preflight", params);
    }

    /**
     * Moves the event's sales to testable, live, paused or ended. A date its season cancelled
     * never opens again: moving it to {@code testable} or {@code live} answers
     * {@code 409 season_date_cancelled}.
     *
     * <p>Single attempt; the {@code expectedRevision} compare-and-set makes a repeat safe to
     * reconcile.
     *
     * @param eventKey the event key
     * @param params the request body: {@code to} and {@code expectedRevision}, and optionally
     *     {@code reason}
     * @return the decoded response
     */
    public Map<String, Object> transitionEventSales(String eventKey, Map<String, Object> params) {
        return http.post("/v1/events/" + encode(eventKey) + "/sales/transition", params);
    }

    /**
     * Retrieves the event's booking fee.
     *
     * @param eventKey the event key
     * @return the decoded response
     */
    public Map<String, Object> retrieveEventBookingFee(String eventKey) {
        return http.get("/v1/events/" + encode(eventKey) + "/booking-fee");
    }

    /**
     * Sets or clears the event's booking fee.
     *
     * <p>Single attempt, never retried automatically. To clear the fee, send
     * {@code bookingFeeMinor} as {@code null} in a map that allows null values.
     *
     * @param eventKey the event key
     * @param params the request body: {@code bookingFeeMinor}
     * @return the decoded response
     */
    public Map<String, Object> replaceEventBookingFee(String eventKey, Map<String, Object> params) {
        return http.put("/v1/events/" + encode(eventKey) + "/booking-fee", params);
    }

    /**
     * Lists the event's discount codes.
     *
     * @param eventKey the event key
     * @return the decoded response
     */
    public Map<String, Object> listEventDiscountCodes(String eventKey) {
        return http.get("/v1/events/" + encode(eventKey) + "/discount-codes");
    }

    /**
     * Creates a discount code for the event.
     *
     * <p>Single attempt, never retried automatically.
     *
     * @param eventKey the event key
     * @param params the request body: {@code code} and {@code kind}, plus any optional fields
     * @return the decoded response
     */
    public Map<String, Object> createEventDiscountCode(String eventKey, Map<String, Object> params) {
        return http.post("/v1/events/" + encode(eventKey) + "/discount-codes", params);
    }

    /**
     * Changes or pauses a discount code.
     *
     * <p>Single attempt, never retried automatically.
     *
     * @param eventKey the event key
     * @param codeId the discount code id
     * @param params the request body: the fields to change
     * @return the decoded response
     */
    public Map<String, Object> updateEventDiscountCode(
            String eventKey, String codeId, Map<String, Object> params) {
        return http.patch(
                "/v1/events/" + encode(eventKey) + "/discount-codes/" + encode(codeId), params);
    }

    /**
     * Ends a discount code for good.
     *
     * <p>Single attempt; ending an ended code again has the same result.
     *
     * @param eventKey the event key
     * @param codeId the discount code id
     * @return the decoded response
     */
    public Map<String, Object> endEventDiscountCode(String eventKey, String codeId) {
        return http.post(
                "/v1/events/" + encode(eventKey) + "/discount-codes/" + encode(codeId) + "/end");
    }

    /**
     * Deletes a discount code nobody has used. The API answers 204 No Content.
     *
     * <p>Single attempt, never retried automatically.
     *
     * @param eventKey the event key
     * @param codeId the discount code id
     */
    public void deleteEventDiscountCode(String eventKey, String codeId) {
        http.delete("/v1/events/" + encode(eventKey) + "/discount-codes/" + encode(codeId));
    }
}
