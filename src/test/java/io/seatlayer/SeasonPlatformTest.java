package io.seatlayer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Holders, orders, event sales and event series: routes, bodies, replay classes, CSV and 204s. */
final class SeasonPlatformTest {

    private static final String BASE = "https://api.seatlayer.io";

    private record Call(String method, String url, Map<String, String> headers, String body) {
    }

    /** One expected request: verb plus path and query, the JSON body or null, and header replay. */
    private record Expected(String request, String body, boolean replay) {
    }

    private final List<Call> calls = new ArrayList<>();

    /** Answers every request with 200 and the same body. */
    private SeatLayer client(String responseBody) {
        return SeatLayer.builder()
                .secretKey("sk_test_abc")
                .transport((method, url, headers, requestBody) -> {
                    calls.add(new Call(method, url, Map.copyOf(headers), requestBody));
                    return new SeatLayerHttpClient.Response(200, responseBody, Map.of());
                })
                .build();
    }

    /** Answers requests from a queue, in order. */
    private SeatLayer client(List<SeatLayerHttpClient.Response> responses) {
        Deque<SeatLayerHttpClient.Response> queue = new ArrayDeque<>(responses);
        return SeatLayer.builder()
                .secretKey("sk_test_abc")
                .transport((method, url, headers, requestBody) -> {
                    calls.add(new Call(method, url, Map.copyOf(headers), requestBody));
                    if (queue.isEmpty()) {
                        throw new AssertionError("more requests than queued responses");
                    }
                    return queue.poll();
                })
                .build();
    }

    private static SeatLayerHttpClient.Response rateLimited() {
        return new SeatLayerHttpClient.Response(
                429, "{\"error\":\"rate_limited\"}", Map.of("retry-after", "0"));
    }

    private void assertCalls(List<Expected> expected) {
        assertEquals(expected.size(), calls.size());
        for (int index = 0; index < expected.size(); index++) {
            Call call = calls.get(index);
            Expected want = expected.get(index);
            String label = "call " + index + " " + want.request();
            assertEquals(want.request(), call.method() + " " + call.url().substring(BASE.length()), label);
            if (want.body() == null) {
                assertNull(call.body(), label);
            } else {
                assertEquals(Json.read(want.body()), Json.read(call.body()), label);
            }
            if (want.replay()) {
                assertTrue(call.headers().containsKey("Idempotency-Key"), label);
            } else {
                assertNull(call.headers().get("Idempotency-Key"), label);
            }
        }
    }

    @Test
    void mapsAll11HolderOperations() {
        SeatLayer sdk = client("{}");
        Holders holders = sdk.holders();
        String ref = "holder a/b";

        holders.listCustomers("fan@example.com", "h/1", "c/1", 10);
        holders.listCustomers();
        holders.retrieveHolder(ref);
        holders.updateHolder(ref, Map.of("email", "new@example.com"), "holder-update-1");
        holders.revokeHolderSessions(ref);
        holders.revokeHolderSessions(ref, Map.of("sessionId", "csn_0123456789abcdef01234567"));
        holders.listHolderSeasons(ref);
        holders.listHolderTickets(ref, "sea/a", 1700000000000L, 1800000000000L, "c/2", 20);
        holders.listHolderTickets(ref);
        holders.retrieveHolderTicket(ref, "tkt/1");
        holders.createHolderTransfer(
                ref, Map.of("ticketIds", List.of("tkt_1"), "toEmail", "friend@example.com"), null);
        holders.listHolderTransfers(ref);
        holders.cancelHolderTransfer(ref, "tr/1");
        holders.acceptHolderTransfer(ref, "tr/1");

        String holder = "/v1/holders/holder%20a%2Fb";
        assertCalls(List.of(
                new Expected(
                        "GET /v1/customers?email=fan%40example.com&holderRef=h%2F1&cursor=c%2F1&limit=10",
                        null, false),
                new Expected("GET /v1/customers", null, false),
                new Expected("GET " + holder, null, false),
                new Expected("PATCH " + holder, "{\"email\":\"new@example.com\"}", true),
                new Expected("POST " + holder + "/sessions/revoke", null, false),
                new Expected(
                        "POST " + holder + "/sessions/revoke",
                        "{\"sessionId\":\"csn_0123456789abcdef01234567\"}", false),
                new Expected("GET " + holder + "/seasons", null, false),
                new Expected(
                        "GET " + holder
                                + "/tickets?seasonKey=sea%2Fa&from=1700000000000&to=1800000000000&cursor=c%2F2&limit=20",
                        null, false),
                new Expected("GET " + holder + "/tickets", null, false),
                new Expected("GET " + holder + "/tickets/tkt%2F1", null, false),
                new Expected(
                        "POST " + holder + "/transfers",
                        "{\"ticketIds\":[\"tkt_1\"],\"toEmail\":\"friend@example.com\"}", true),
                new Expected("GET " + holder + "/transfers", null, false),
                new Expected("POST " + holder + "/transfers/tr%2F1/cancel", null, false),
                new Expected("POST " + holder + "/transfers/tr%2F1/accept", null, false)));
        assertEquals("holder-update-1", calls.get(3).headers().get("Idempotency-Key"));
    }

    @Test
    void mapsAll9OrderOperations() {
        SeatLayer sdk = client("{}");
        Orders orders = sdk.orders();

        orders.listOrders("ev/1", "sea/a", "confirmed", 1700000000000L, 1800000000000L, "c/1", 50);
        orders.listOrders();
        orders.retrieveOrder("ord/1");
        orders.listOrderTickets("ord/1");
        orders.listEventOrders("ev/1", "ann lee", "refunded", "card", 1L, 2L, "c/2", 25);
        orders.listEventOrders("ev/1");
        orders.retrieveEventOrder("ev/1", "ord/1");
        orders.exportEventOrdersCsv("ev/1", "ann", "confirmed", "card", 1L, 2L);
        orders.refundEventOrder("ev/1", "ord/1", Map.of("amountMinor", 250), "event-refund-1");
        orders.refundEventOrder("ev/1", "ord/1", null);
        orders.cancelEventOrder(
                "ev/1", "ord/1", Map.of("reason", "event_cancelled", "notifyBuyer", true), "event-cancel-1");
        orders.resendEventOrderTickets("ev/1", "ord/1");
        orders.resendEventOrderTickets("ev/1", "ord/1", Map.of("email", "fan@example.com"));

        String order = "/v1/events/ev%2F1/orders/ord%2F1";
        assertCalls(List.of(
                new Expected(
                        "GET /v1/orders?eventKey=ev%2F1&seasonKey=sea%2Fa&status=confirmed"
                                + "&from=1700000000000&to=1800000000000&cursor=c%2F1&limit=50",
                        null, false),
                new Expected("GET /v1/orders", null, false),
                new Expected("GET /v1/orders/ord%2F1", null, false),
                new Expected("GET /v1/orders/ord%2F1/tickets", null, false),
                new Expected(
                        "GET /v1/events/ev%2F1/orders?q=ann+lee&status=refunded&paidBy=card"
                                + "&from=1&to=2&cursor=c%2F2&limit=25",
                        null, false),
                new Expected("GET /v1/events/ev%2F1/orders", null, false),
                new Expected("GET " + order, null, false),
                new Expected(
                        "GET /v1/events/ev%2F1/orders.csv?q=ann&status=confirmed&paidBy=card&from=1&to=2",
                        null, false),
                new Expected("POST " + order + "/refund", "{\"amountMinor\":250}", true),
                new Expected("POST " + order + "/refund", null, true),
                new Expected(
                        "POST " + order + "/cancel",
                        "{\"reason\":\"event_cancelled\",\"notifyBuyer\":true}", true),
                new Expected("POST " + order + "/resend", null, false),
                new Expected("POST " + order + "/resend", "{\"email\":\"fan@example.com\"}", false)));
        assertEquals("event-refund-1", calls.get(8).headers().get("Idempotency-Key"));
        assertEquals("event-cancel-1", calls.get(10).headers().get("Idempotency-Key"));
    }

    @Test
    void mapsAll14EventSalesOperations() {
        SeatLayer sdk = client("{}");
        Events events = sdk.events();
        String key = "ev/1";
        Map<String, Object> clearFee = new LinkedHashMap<>();
        clearFee.put("bookingFeeMinor", null);

        events.retrieveEventBuyerCheckout(key);
        events.replaceEventBuyerCheckout(key, Map.of("buyerCheckout", "seatlayer", "expectedRevision", 3));
        events.retrieveEventPaymentGateway(key, "EUR");
        events.retrieveEventPaymentGateway(key);
        events.replaceEventPaymentGateway(key, Map.of("connectionId", "conn_1"));
        events.replaceEventPaymentGateway(key);
        events.retrieveEventSalesReadiness(key);
        events.preflightEventSales(key, Map.of("orderId", "ord_1"));
        events.transitionEventSales(key, Map.of("to", "live", "expectedRevision", 4));
        events.retrieveEventBookingFee(key);
        events.replaceEventBookingFee(key, Map.of("bookingFeeMinor", 150));
        events.replaceEventBookingFee(key, clearFee);
        events.listEventDiscountCodes(key);
        events.createEventDiscountCode(key, Map.of("code", "EARLY", "kind", "percent", "percent", 10));
        events.updateEventDiscountCode(key, "code/1", Map.of("active", false));
        events.endEventDiscountCode(key, "code/1");
        events.deleteEventDiscountCode(key, "code/1");

        String event = "/v1/events/ev%2F1";
        assertCalls(List.of(
                new Expected("GET " + event + "/buyer-checkout", null, false),
                new Expected(
                        "PUT " + event + "/buyer-checkout",
                        "{\"buyerCheckout\":\"seatlayer\",\"expectedRevision\":3}", false),
                new Expected("GET " + event + "/payment-gateway?currency=EUR", null, false),
                new Expected("GET " + event + "/payment-gateway", null, false),
                new Expected("PUT " + event + "/payment-gateway", "{\"connectionId\":\"conn_1\"}", false),
                new Expected("PUT " + event + "/payment-gateway", null, false),
                new Expected("GET " + event + "/sales/readiness", null, false),
                new Expected("POST " + event + "/sales/preflight", "{\"orderId\":\"ord_1\"}", false),
                new Expected(
                        "POST " + event + "/sales/transition", "{\"to\":\"live\",\"expectedRevision\":4}", false),
                new Expected("GET " + event + "/booking-fee", null, false),
                new Expected("PUT " + event + "/booking-fee", "{\"bookingFeeMinor\":150}", false),
                new Expected("PUT " + event + "/booking-fee", "{\"bookingFeeMinor\":null}", false),
                new Expected("GET " + event + "/discount-codes", null, false),
                new Expected(
                        "POST " + event + "/discount-codes",
                        "{\"code\":\"EARLY\",\"kind\":\"percent\",\"percent\":10}", false),
                new Expected("PATCH " + event + "/discount-codes/code%2F1", "{\"active\":false}", false),
                new Expected("POST " + event + "/discount-codes/code%2F1/end", null, false),
                new Expected("DELETE " + event + "/discount-codes/code%2F1", null, false)));
    }

    @Test
    void mapsAll5EventSeriesOperations() {
        SeatLayer sdk = client("{}");
        EventSeries series = sdk.eventSeries();

        series.createEventSeries(
                Map.of(
                        "name", "Summer nights",
                        "chartKey", "chart_1",
                        "schedule", Map.of("repeat", "weekly", "count", 4)),
                "series-1");
        series.listEventSeries(10, "c/1");
        series.listEventSeries();
        series.retrieveEventSeries("ev/1");
        series.addEventSeriesDates("ev/1", Map.of("dates", List.of("2027-06-01T19:30")), null);
        series.updateEventSeries("ev/1", Map.of("name", "Summer nights 2027"), "series-update-1");

        assertCalls(List.of(
                new Expected(
                        "POST /v1/event-series",
                        "{\"name\":\"Summer nights\",\"chartKey\":\"chart_1\","
                                + "\"schedule\":{\"repeat\":\"weekly\",\"count\":4}}",
                        true),
                new Expected("GET /v1/event-series?limit=10&cursor=c%2F1", null, false),
                new Expected("GET /v1/event-series", null, false),
                new Expected("GET /v1/event-series/ev%2F1", null, false),
                new Expected(
                        "POST /v1/event-series/ev%2F1/dates", "{\"dates\":[\"2027-06-01T19:30\"]}", true),
                new Expected("PATCH /v1/event-series/ev%2F1", "{\"name\":\"Summer nights 2027\"}", true)));
        assertEquals("series-1", calls.get(0).headers().get("Idempotency-Key"));
        assertEquals("series-update-1", calls.get(5).headers().get("Idempotency-Key"));
    }

    @Test
    void csvExportsReturnTheRawText() {
        // Not JSON on purpose: decoding it would throw.
        String csv = "order,buyer\nord_1,\"Lee, Ann\"\n";
        SeatLayer sdk = client(csv);

        assertEquals(csv, sdk.seasons().exportSeasonHoldersCsv("sea/a"));
        assertEquals(csv, sdk.seasons().exportSeasonOrdersCsv("sea/a"));
        assertEquals(csv, sdk.orders().exportEventOrdersCsv("ev/1"));

        assertEquals(3, calls.size());
        assertEquals(BASE + "/v1/seasons/sea%2Fa/reports/holders.csv", calls.get(0).url());
        assertEquals(BASE + "/v1/seasons/sea%2Fa/reports/orders.csv", calls.get(1).url());
        assertEquals(BASE + "/v1/events/ev%2F1/orders.csv", calls.get(2).url());
        for (Call call : calls) {
            assertEquals("GET", call.method());
            assertTrue(call.headers().get("Accept").startsWith("text/csv"));
            assertNull(call.headers().get("Idempotency-Key"));
        }
    }

    @Test
    void noContentDeletesReturnNothing() {
        SeatLayer sdk = client(List.of(
                new SeatLayerHttpClient.Response(204, "", Map.of()),
                new SeatLayerHttpClient.Response(204, "", Map.of())));

        sdk.seasons().deleteSeasonPlan("sea/a", "plan/1", "plan-delete-2");
        sdk.events().deleteEventDiscountCode("ev/1", "code/1");

        assertCalls(List.of(
                new Expected("DELETE /v1/seasons/sea%2Fa/plans/plan%2F1", null, true),
                new Expected("DELETE /v1/events/ev%2F1/discount-codes/code%2F1", null, false)));
        assertEquals("plan-delete-2", calls.get(0).headers().get("Idempotency-Key"));
    }

    @Test
    void headerReplayOperationsRetryWithOneKey() {
        SeatLayer sdk = client(List.of(
                rateLimited(),
                new SeatLayerHttpClient.Response(200, "{\"ok\":true}", Map.of())));

        sdk.orders().refundEventOrder("ev_1", "ord_1", null);

        assertEquals(2, calls.size());
        assertEquals(
                calls.get(0).headers().get("Idempotency-Key"), calls.get(1).headers().get("Idempotency-Key"));
    }

    @Test
    void unsupportedAndDomainExactOperationsAreSingleAttempt() {
        SeatLayer resend = client(List.of(rateLimited()));
        assertThrows(
                SeatLayerRateLimitException.class,
                () -> resend.orders().resendEventOrderTickets("ev_1", "ord_1"));
        assertEquals(1, calls.size());
        assertNull(calls.get(0).headers().get("Idempotency-Key"));

        calls.clear();
        SeatLayer rotate = client(List.of(rateLimited()));
        assertThrows(
                SeatLayerRateLimitException.class,
                () -> rotate.seasons().rotateSeasonRenewalOfferLink("sea_1", "offer_1"));
        assertEquals(1, calls.size());
        assertNull(calls.get(0).headers().get("Idempotency-Key"));

        calls.clear();
        SeatLayer discount = client(List.of(rateLimited()));
        assertThrows(
                SeatLayerRateLimitException.class,
                () -> discount.events().createEventDiscountCode(
                        "ev_1", Map.of("code", "EARLY", "kind", "percent", "percent", 10)));
        assertEquals(1, calls.size());
        assertNull(calls.get(0).headers().get("Idempotency-Key"));

        calls.clear();
        SeatLayer transfer = client(List.of(rateLimited()));
        assertThrows(
                SeatLayerRateLimitException.class,
                () -> transfer.holders().acceptHolderTransfer("holder_1", "tr_1"));
        assertEquals(1, calls.size());
        assertNull(calls.get(0).headers().get("Idempotency-Key"));
    }
}
