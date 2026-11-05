package io.seatlayer;

import static io.seatlayer.SeatLayerHttpClient.body;
import static io.seatlayer.SeatLayerHttpClient.encode;

import java.util.Map;

/**
 * Event series: many dated events planned from one schedule and changed together.
 *
 * <p>A series is addressed by its SeatLayer event key.
 */
public final class EventSeries {

    private final SeatLayerHttpClient http;

    EventSeries(SeatLayerHttpClient http) {
        this.http = http;
    }

    private static String path(String eventKey, String suffix) {
        return "/v1/event-series/" + encode(eventKey) + suffix;
    }

    /**
     * Plans an event series from a schedule, or makes it as a job.
     *
     * <p>Header replay: the same idempotency key is reused on every retry. The API answers
     * 202 when the dates are made as a job.
     *
     * @param params the request body: {@code name}, {@code chartKey} and {@code schedule},
     *     plus any optional fields such as {@code dryRun}
     * @param idempotencyKey a stable key, or {@code null} to generate one
     * @return the decoded response
     */
    public Map<String, Object> createEventSeries(Map<String, Object> params, String idempotencyKey) {
        return http.postWithHeaderReplay("/v1/event-series", params, idempotencyKey);
    }

    /**
     * Lists the first page of event series.
     *
     * @return the decoded response
     */
    public Map<String, Object> listEventSeries() {
        return listEventSeries(null, null);
    }

    /**
     * Lists event series. Null arguments are not sent.
     *
     * @param limit the page size, or {@code null} for the server default
     * @param cursor the cursor from the previous page, or {@code null}
     * @return the decoded response
     */
    public Map<String, Object> listEventSeries(Integer limit, String cursor) {
        return http.get("/v1/event-series", body("limit", limit, "cursor", cursor));
    }

    /**
     * Retrieves an event series, its dates and its job.
     *
     * @param eventKey the series' event key
     * @return the decoded response
     */
    public Map<String, Object> retrieveEventSeries(String eventKey) {
        return http.get(path(eventKey, ""));
    }

    /**
     * Adds dates to an event series, copied from its first date.
     *
     * <p>Header replay: the same idempotency key is reused on every retry.
     *
     * @param eventKey the series' event key
     * @param params the request body: either {@code schedule} or {@code dates}
     * @param idempotencyKey a stable key, or {@code null} to generate one
     * @return the decoded response
     */
    public Map<String, Object> addEventSeriesDates(
            String eventKey, Map<String, Object> params, String idempotencyKey) {
        return http.postWithHeaderReplay(path(eventKey, "/dates"), params, idempotencyKey);
    }

    /**
     * Changes the name, prices or sales of every upcoming date in the series.
     *
     * <p>Header replay: the same idempotency key is reused on every retry.
     *
     * @param eventKey the series' event key
     * @param params the request body: any of {@code name}, {@code prices} and {@code sales}
     * @param idempotencyKey a stable key, or {@code null} to generate one
     * @return the decoded response
     */
    public Map<String, Object> updateEventSeries(
            String eventKey, Map<String, Object> params, String idempotencyKey) {
        return http.mutationWithHeaderReplay("PATCH", path(eventKey, ""), params, idempotencyKey);
    }
}
