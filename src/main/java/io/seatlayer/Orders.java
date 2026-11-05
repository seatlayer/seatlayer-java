package io.seatlayer;

import static io.seatlayer.SeatLayerHttpClient.body;
import static io.seatlayer.SeatLayerHttpClient.encode;

import java.util.Map;

/**
 * Orders SeatLayer took: the order list across events and seasons, one event's orders,
 * refunds, cancellations and ticket emails.
 *
 * <p>Season order refunds and cancellations live on {@link Seasons}.
 */
public final class Orders {

    private final SeatLayerHttpClient http;

    Orders(SeatLayerHttpClient http) {
        this.http = http;
    }

    /**
     * Lists the first page of orders across events and seasons.
     *
     * @return the decoded response
     */
    public Map<String, Object> listOrders() {
        return listOrders(null, null, null, null, null, null, null);
    }

    /**
     * Lists orders across events and seasons. Null filters are not sent.
     *
     * @param eventKey only this event's orders, or {@code null}
     * @param seasonKey only this Season's orders, renewals included, or {@code null}
     * @param status only orders in this state, or {@code null}
     * @param from created at or after this time, in epoch milliseconds, or {@code null}
     * @param to created before this time, in epoch milliseconds, or {@code null}
     * @param cursor the cursor from the previous page, or {@code null}
     * @param limit the page size, or {@code null} for the server default
     * @return the decoded response
     */
    public Map<String, Object> listOrders(
            String eventKey,
            String seasonKey,
            String status,
            Long from,
            Long to,
            String cursor,
            Integer limit) {
        return http.get(
                "/v1/orders",
                body(
                        "eventKey", eventKey,
                        "seasonKey", seasonKey,
                        "status", status,
                        "from", from,
                        "to", to,
                        "cursor", cursor,
                        "limit", limit));
    }

    /**
     * Retrieves an order and its lines.
     *
     * @param orderId the order id
     * @return the decoded response
     */
    public Map<String, Object> retrieveOrder(String orderId) {
        return http.get("/v1/orders/" + encode(orderId));
    }

    /**
     * Lists an order's tickets with their scan links.
     *
     * @param orderId the order id
     * @return the decoded response
     */
    public Map<String, Object> listOrderTickets(String orderId) {
        return http.get("/v1/orders/" + encode(orderId) + "/tickets");
    }

    /**
     * Lists the first page of one event's orders.
     *
     * @param eventKey the event key
     * @return the decoded response
     */
    public Map<String, Object> listEventOrders(String eventKey) {
        return listEventOrders(eventKey, null, null, null, null, null, null, null);
    }

    /**
     * Lists one event's orders. Null filters are not sent.
     *
     * @param eventKey the event key
     * @param q search by buyer name, email, order number or seat, or {@code null}
     * @param status only orders in this state, or {@code null}
     * @param paidBy only orders paid this way, or {@code null}
     * @param from paid at or after this time, in epoch milliseconds, or {@code null}
     * @param to paid before this time, in epoch milliseconds, or {@code null}
     * @param cursor the cursor from the previous page, or {@code null}
     * @param limit the page size, or {@code null} for the server default
     * @return the decoded response
     */
    public Map<String, Object> listEventOrders(
            String eventKey,
            String q,
            String status,
            String paidBy,
            Long from,
            Long to,
            String cursor,
            Integer limit) {
        return http.get(
                "/v1/events/" + encode(eventKey) + "/orders",
                body(
                        "q", q,
                        "status", status,
                        "paidBy", paidBy,
                        "from", from,
                        "to", to,
                        "cursor", cursor,
                        "limit", limit));
    }

    /**
     * Retrieves one event order with its guests and tickets.
     *
     * @param eventKey the event key
     * @param orderId the order id
     * @return the decoded response
     */
    public Map<String, Object> retrieveEventOrder(String eventKey, String orderId) {
        return http.get("/v1/events/" + encode(eventKey) + "/orders/" + encode(orderId));
    }

    /**
     * Downloads all of one event's orders as CSV.
     *
     * @param eventKey the event key
     * @return the CSV text, exactly as the API sent it
     */
    public String exportEventOrdersCsv(String eventKey) {
        return exportEventOrdersCsv(eventKey, null, null, null, null, null);
    }

    /**
     * Downloads one event's matching orders as CSV. Null filters are not sent.
     *
     * @param eventKey the event key
     * @param q search by buyer name, email, order number or seat, or {@code null}
     * @param status only orders in this state, or {@code null}
     * @param paidBy only orders paid this way, or {@code null}
     * @param from paid at or after this time, in epoch milliseconds, or {@code null}
     * @param to paid before this time, in epoch milliseconds, or {@code null}
     * @return the CSV text, exactly as the API sent it
     */
    public String exportEventOrdersCsv(
            String eventKey, String q, String status, String paidBy, Long from, Long to) {
        return http.getText(
                "/v1/events/" + encode(eventKey) + "/orders.csv",
                body("q", q, "status", status, "paidBy", paidBy, "from", from, "to", to));
    }

    /**
     * Refunds an event order in full through its payment provider.
     *
     * <p>Header replay: the same idempotency key is reused on every retry.
     *
     * @param eventKey the event key
     * @param orderId the order id
     * @param idempotencyKey a stable key, or {@code null} to generate one
     * @return the decoded response
     */
    public Map<String, Object> refundEventOrder(
            String eventKey, String orderId, String idempotencyKey) {
        return refundEventOrder(eventKey, orderId, null, idempotencyKey);
    }

    /**
     * Refunds an event order in full or in part through its payment provider.
     *
     * <p>Header replay: the same idempotency key is reused on every retry.
     *
     * @param eventKey the event key
     * @param orderId the order id
     * @param params the optional request body ({@code amountMinor}), or {@code null} for a full refund
     * @param idempotencyKey a stable key, or {@code null} to generate one
     * @return the decoded response
     */
    public Map<String, Object> refundEventOrder(
            String eventKey, String orderId, Map<String, Object> params, String idempotencyKey) {
        return http.postWithHeaderReplay(
                "/v1/events/" + encode(eventKey) + "/orders/" + encode(orderId) + "/refund",
                params,
                idempotencyKey);
    }

    /**
     * Cancels an event order without moving money.
     *
     * <p>Header replay: the same idempotency key is reused on every retry.
     *
     * @param eventKey the event key
     * @param orderId the order id
     * @param params the request body: {@code reason}, plus any optional fields
     * @param idempotencyKey a stable key, or {@code null} to generate one
     * @return the decoded response
     */
    public Map<String, Object> cancelEventOrder(
            String eventKey, String orderId, Map<String, Object> params, String idempotencyKey) {
        return http.postWithHeaderReplay(
                "/v1/events/" + encode(eventKey) + "/orders/" + encode(orderId) + "/cancel",
                params,
                idempotencyKey);
    }

    /**
     * Emails an event order's tickets again, to the address on the order.
     *
     * <p>Single attempt, never retried automatically.
     *
     * @param eventKey the event key
     * @param orderId the order id
     * @return the decoded response
     */
    public Map<String, Object> resendEventOrderTickets(String eventKey, String orderId) {
        return resendEventOrderTickets(eventKey, orderId, null);
    }

    /**
     * Emails an event order's tickets again.
     *
     * <p>Single attempt, never retried automatically.
     *
     * @param eventKey the event key
     * @param orderId the order id
     * @param params the optional request body ({@code email}), or {@code null}
     * @return the decoded response
     */
    public Map<String, Object> resendEventOrderTickets(
            String eventKey, String orderId, Map<String, Object> params) {
        return http.post(
                "/v1/events/" + encode(eventKey) + "/orders/" + encode(orderId) + "/resend",
                params);
    }
}
