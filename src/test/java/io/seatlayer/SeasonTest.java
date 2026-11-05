package io.seatlayer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class SeasonTest {

    private record Call(String method, String url, Map<String, String> headers, String body) {
    }

    @Test
    void mapsAll87SeasonOperationsAndReplayClasses() {
        List<Call> calls = new ArrayList<>();
        SeatLayer sdk = SeatLayer.builder()
                .secretKey("sk_test_abc")
                .transport((method, url, headers, requestBody) -> {
                    calls.add(new Call(method, url, Map.copyOf(headers), requestBody));
                    return new SeatLayerHttpClient.Response(200, "{}", Map.of());
                })
                .build();
        Seasons seasons = sdk.seasons();
        String key = "sea/a";

        seasons.listSeasons("ws 1", "draft", 20, "c/1");
        seasons.validateSeason(Map.of("sourcePerformanceGroupKeys", List.of("pg_1")));
        seasons.createSeason(Map.of("name", "Series", "eventKeys", List.of("ev_1", "ev_2")), "create-1");
        seasons.retrieveSeason(key);
        seasons.updateSeason(key, Map.of("expectedRevision", 1, "name", "Series 2"), "update-1");
        seasons.deleteSeason(key, "delete-1");
        seasons.activateSeason(key, 1);
        seasons.closeSeason(key, 2);
        seasons.archiveSeason(key, 3);
        seasons.retrieveSeasonLifecycle(key, "life/1");
        seasons.createSeasonPlan(key, Map.of("name", "Plan", "eventKeys", List.of("ev_1", "ev_2")), "plan-1");
        seasons.retrieveSeasonPlan(key, "plan/1");
        seasons.publishSeasonPlan(key, "plan/1", 2);
        seasons.supersedeSeasonPlan(key, "plan/1", 3);
        seasons.openSeasonSales(key, 3);
        seasons.pauseSeasonSales(key, 4);
        seasons.resumeSeasonSales(key, 5);
        seasons.endSeasonSales(key, 6);
        seasons.duplicateSeasonToLive(key, Map.of("eventKeys", List.of("live_1", "live_2")), "live-1");
        seasons.createSeasonBuyerAccessSession(key, Map.of("allowedOrigin", "https://tickets.example", "includePublic", true));
        seasons.listSeasonBuyerAccessSessions(key, 10);
        seasons.revokeSeasonBuyerAccessSession(key, "session/1");
        seasons.retrieveSeasonHold(key, "hold/1");
        seasons.bookSeasonHold(key, "hold/1", "book_1", "order_1");
        seasons.retrieveSeasonBooking(key, "book/1");
        seasons.cancelSeasonBooking(key, "book/1", "cancel_1", "order_1", "pa_1", "release");
        seasons.validateSeasonBuyerRehearsal(key);
        seasons.createSeasonHolderImport(key, Map.of("successorPlanActivationId", "pa_1", "rows", List.of()), "import-1");
        seasons.retrieveSeasonHolderImport(key, "import/1");
        seasons.createSeasonRenewalOffers(key, Map.of("deadlineAt", 123), "offers-1");
        seasons.listSeasonRenewalOffers(key);
        seasons.retrieveSeasonRenewalOffer(key, "offer/1");
        seasons.extendSeasonRenewalOffer(key, "offer/1", 456);
        seasons.inspectSeasonRenewalOffer(key, "offer/1");
        seasons.commitSeasonRenewalOffer(key, "offer/1", "commit_1", "order_1", "book_1", "pa_1");
        seasons.declineSeasonRenewalOffer(key, "offer/1");
        seasons.releaseSeasonRenewalOffer(key, "offer/1");
        seasons.listSeasonOccurrences(key);
        seasons.createSeasonAmendment(key, Map.of("eventKey", "ev_1", "kind", "reschedule"), "amend-1");
        seasons.listSeasonAmendments(key);
        seasons.retrieveSeasonAmendment(key, "amend/1");
        seasons.retrieveSeasonReport(key);
        seasons.listSeasonOperations(key);
        seasons.retrieveSeasonSupportLookup(key, null, "holder a/b");
        seasons.listSeasonOutbox(key);
        seasons.replaySeasonOutbox(key, "occurrence/1");
        seasons.listSeasonAudit(key);
        seasons.exportSeasonSupportSnapshot(key);
        seasons.createSeasonHolderImportFromSeason(
                key, Map.of("sourceSeasonId", "sea_2026", "successorPlanActivationId", "pa_1"), "from-1");
        seasons.listSeasonPerformances(key);
        seasons.createSeasonPerformances(
                key,
                Map.of("titlePattern", "Match", "timezone", "Europe/London", "repeat", "weekly", "count", 3),
                "perf-1");
        seasons.attachSeasonPerformances(key, Map.of("eventKeys", List.of("ev_1")), null);
        seasons.deleteSeasonPerformance(key, "ev/1", "perf-del-1");
        seasons.updateSeasonPlan(key, "plan/1", Map.of("name", "Gold"), "plan-upd-1");
        seasons.deleteSeasonPlan(key, "plan/2", "plan-del-1");
        seasons.publishSeason(key, "publish-1");
        seasons.createSeasonCheckoutTest(key, Map.of("planKey", "plan/1", "quantity", 2), "test-1");
        seasons.listSeasonCheckoutTests(key);
        seasons.replaceSeasonPackagePrices(
                key, "plan/1", Map.of("prices", List.of(Map.of("category", "A", "amountMinor", 5000))), "prices-1");
        seasons.listSeasonPrices(key);
        seasons.attachSeasonPlanPerformance(key, "plan/1", Map.of("eventKey", "ev_3"), "attach-1");
        seasons.deleteSeasonPlanPerformance(key, "plan/1", "ev/3", "detach-1");
        seasons.markSeasonPerformancePast(key, "ev/1", Map.of("force", true), "past-1");
        seasons.returnSeasonOccurrenceInventory(key, "ev/1", Map.of(
                "actionId", "ret_1", "bookingRef", "order_1", "planActivationId", "pa_1",
                "labels", List.of("A-1"), "source", Map.of("kind", "holder")));
        seasons.reclaimSeasonOccurrenceInventory(key, "ev/1", Map.of(
                "actionId", "rec_1", "returnActionId", "ret_1", "bookingRef", "order_1",
                "planActivationId", "pa_1", "labels", List.of("A-1"), "source", Map.of("kind", "holder")));
        seasons.listSeasonHolders(key, "ann lee", "active", "c/2", 25);
        seasons.refundSeasonOrder(key, "ord/1", Map.of("amountMinor", 500), "refund-1");
        seasons.cancelSeasonOrder(key, "ord/1", Map.of("reason", "customer_request"), "cancel-order-1");
        seasons.cancelSeasonOrderDate(key, "ord/1", "ev/2", "cancel-date-1");
        seasons.resendSeasonOrderTickets(key, "ord/1", Map.of("email", "fan@example.com"));
        seasons.retrieveSeasonHostedRenewals(key);
        seasons.rotateSeasonRenewalOfferLink(key, "offer/1");
        seasons.updateSeasonRenewalOfferContact(
                key, "offer/1", Map.of("email", "fan@example.com", "name", "Ann Lee"));
        seasons.createSeasonRenewalNotices(key);
        seasons.resendSeasonRenewalOffer(key, "offer/1");
        seasons.retrieveSeasonSalesReport(key);
        seasons.retrieveSeasonSeatsReport(key);
        String holdersCsv = seasons.exportSeasonHoldersCsv(key, "ann", "active");
        String ordersCsv = seasons.exportSeasonOrdersCsv(key);
        seasons.retrieveSeasonSeatCounts(key);
        seasons.refundSeasonPerformance(
                key, "ev/1", Map.of("amount", "per_date", "reason", "Storm"), "refund-date-1");
        seasons.listSeasonBulkJobs(key);
        seasons.retrieveSeasonBulkJob(key, "job/1");
        seasons.retrieveSeasonRenewalQuote(key, "offer/1");
        seasons.createSeasonRenewalIntent(key, "offer/1");
        seasons.createSeasonRenewalCheckout(key, "offer/1", Map.of(
                "returnUrl", "https://tickets.example/renewed", "buyerEmail", "ana@example.com",
                "buyerName", "Ana Silva", "provider", "stripe"), "renew-1");
        seasons.releaseSeasonLapsedOffers(key, "release-lapsed-1");

        assertEquals(87, calls.size());
        assertEquals("{}", holdersCsv);
        assertEquals("{}", ordersCsv);
        assertNull(calls.get(26).body());
        List<String> expected = List.of(
                "GET /v1/seasons?workspaceId=ws+1&structureState=draft&limit=20&cursor=c%2F1",
                "POST /v1/seasons/validate", "POST /v1/seasons", "GET /v1/seasons/sea%2Fa",
                "PATCH /v1/seasons/sea%2Fa", "DELETE /v1/seasons/sea%2Fa",
                "POST /v1/seasons/sea%2Fa/activate", "POST /v1/seasons/sea%2Fa/close",
                "POST /v1/seasons/sea%2Fa/archive", "GET /v1/seasons/sea%2Fa/lifecycle/life%2F1",
                "POST /v1/seasons/sea%2Fa/plans", "GET /v1/seasons/sea%2Fa/plans/plan%2F1",
                "POST /v1/seasons/sea%2Fa/plans/plan%2F1/publish",
                "POST /v1/seasons/sea%2Fa/plans/plan%2F1/supersede",
                "POST /v1/seasons/sea%2Fa/sales/open", "POST /v1/seasons/sea%2Fa/sales/pause",
                "POST /v1/seasons/sea%2Fa/sales/resume", "POST /v1/seasons/sea%2Fa/sales/end",
                "POST /v1/seasons/sea%2Fa/duplicate-to-live",
                "POST /v1/seasons/sea%2Fa/buyer-access-sessions",
                "GET /v1/seasons/sea%2Fa/buyer-access-sessions?limit=10",
                "DELETE /v1/seasons/sea%2Fa/buyer-access-sessions/session%2F1",
                "GET /v1/seasons/sea%2Fa/holds/hold%2F1",
                "POST /v1/seasons/sea%2Fa/holds/hold%2F1/book",
                "GET /v1/seasons/sea%2Fa/bookings/book%2F1",
                "POST /v1/seasons/sea%2Fa/bookings/book%2F1/cancel",
                "POST /v1/seasons/sea%2Fa/buyer-rehearsals/validate",
                "POST /v1/seasons/sea%2Fa/imports", "GET /v1/seasons/sea%2Fa/imports/import%2F1",
                "POST /v1/seasons/sea%2Fa/renewal-offers", "GET /v1/seasons/sea%2Fa/renewal-offers",
                "GET /v1/seasons/sea%2Fa/renewal-offers/offer%2F1",
                "POST /v1/seasons/sea%2Fa/renewal-offers/offer%2F1/extend",
                "GET /v1/seasons/sea%2Fa/renewal-offers/offer%2F1/inspect",
                "POST /v1/seasons/sea%2Fa/renewal-offers/offer%2F1/commit",
                "POST /v1/seasons/sea%2Fa/renewal-offers/offer%2F1/decline",
                "POST /v1/seasons/sea%2Fa/renewal-offers/offer%2F1/release",
                "GET /v1/seasons/sea%2Fa/occurrences", "POST /v1/seasons/sea%2Fa/amendments",
                "GET /v1/seasons/sea%2Fa/amendments", "GET /v1/seasons/sea%2Fa/amendments/amend%2F1",
                "GET /v1/seasons/sea%2Fa/reports", "GET /v1/seasons/sea%2Fa/operations",
                "GET /v1/seasons/sea%2Fa/support-lookups?holderRef=holder+a%2Fb",
                "GET /v1/seasons/sea%2Fa/outbox",
                "POST /v1/seasons/sea%2Fa/outbox/occurrence%2F1/replay",
                "GET /v1/seasons/sea%2Fa/audit", "GET /v1/seasons/sea%2Fa/export",
                "POST /v1/seasons/sea%2Fa/imports/from-season",
                "GET /v1/seasons/sea%2Fa/performances",
                "POST /v1/seasons/sea%2Fa/performances",
                "POST /v1/seasons/sea%2Fa/performances/attach",
                "DELETE /v1/seasons/sea%2Fa/performances/ev%2F1",
                "PATCH /v1/seasons/sea%2Fa/plans/plan%2F1",
                "DELETE /v1/seasons/sea%2Fa/plans/plan%2F2",
                "POST /v1/seasons/sea%2Fa/publish",
                "POST /v1/seasons/sea%2Fa/checkout-tests",
                "GET /v1/seasons/sea%2Fa/checkout-tests",
                "PUT /v1/seasons/sea%2Fa/plans/plan%2F1/prices",
                "GET /v1/seasons/sea%2Fa/prices",
                "POST /v1/seasons/sea%2Fa/plans/plan%2F1/performances",
                "DELETE /v1/seasons/sea%2Fa/plans/plan%2F1/performances/ev%2F3",
                "POST /v1/seasons/sea%2Fa/performances/ev%2F1/past",
                "POST /v1/seasons/sea%2Fa/occurrences/ev%2F1/returns",
                "POST /v1/seasons/sea%2Fa/occurrences/ev%2F1/reclaims",
                "GET /v1/seasons/sea%2Fa/holders?q=ann+lee&status=active&cursor=c%2F2&limit=25",
                "POST /v1/seasons/sea%2Fa/orders/ord%2F1/refund",
                "POST /v1/seasons/sea%2Fa/orders/ord%2F1/cancel",
                "POST /v1/seasons/sea%2Fa/orders/ord%2F1/dates/ev%2F2/cancel",
                "POST /v1/seasons/sea%2Fa/orders/ord%2F1/resend",
                "GET /v1/seasons/sea%2Fa/renewal-hosted",
                "POST /v1/seasons/sea%2Fa/renewal-offers/offer%2F1/link",
                "POST /v1/seasons/sea%2Fa/renewal-offers/offer%2F1/contact",
                "POST /v1/seasons/sea%2Fa/renewal-notices",
                "POST /v1/seasons/sea%2Fa/renewal-offers/offer%2F1/resend",
                "GET /v1/seasons/sea%2Fa/reports/sales",
                "GET /v1/seasons/sea%2Fa/reports/seats",
                "GET /v1/seasons/sea%2Fa/reports/holders.csv?q=ann&status=active",
                "GET /v1/seasons/sea%2Fa/reports/orders.csv",
                "GET /v1/seasons/sea%2Fa/seat-counts",
                "POST /v1/seasons/sea%2Fa/performances/ev%2F1/refunds",
                "GET /v1/seasons/sea%2Fa/jobs",
                "GET /v1/seasons/sea%2Fa/jobs/job%2F1",
                "GET /v1/seasons/sea%2Fa/renewal-offers/offer%2F1/quote",
                "POST /v1/seasons/sea%2Fa/renewal-offers/offer%2F1/intent",
                "POST /v1/seasons/sea%2Fa/renewal-offers/offer%2F1/checkout",
                "POST /v1/seasons/sea%2Fa/renewal-offers/release-lapsed");
        List<String> actual = calls.stream()
                .map(call -> call.method() + " " + call.url().substring("https://api.seatlayer.io".length()))
                .toList();
        assertEquals(expected, actual);

        List<Integer> replayIndexes = List.of(
                2, 4, 5, 10, 18, 27, 29, 38,
                48, 50, 51, 52, 53, 54, 55, 56, 58, 60, 61, 62, 66, 67, 68, 80, 85, 86);
        for (int index = 0; index < calls.size(); index++) {
            if (replayIndexes.contains(index)) {
                assertTrue(calls.get(index).headers().containsKey("Idempotency-Key"));
            } else {
                assertNull(calls.get(index).headers().get("Idempotency-Key"));
            }
        }
        assertEquals("from-1", calls.get(48).headers().get("Idempotency-Key"));
        assertEquals("plan-del-1", calls.get(54).headers().get("Idempotency-Key"));
        assertEquals("cancel-date-1", calls.get(68).headers().get("Idempotency-Key"));
        assertEquals("refund-date-1", calls.get(80).headers().get("Idempotency-Key"));
        assertEquals("renew-1", calls.get(85).headers().get("Idempotency-Key"));
        assertEquals("release-lapsed-1", calls.get(86).headers().get("Idempotency-Key"));

        // JSON bodies of the 39 operations added after the first 48, in call order; null means no body.
        List<String> addedBodies = Arrays.asList(
                "{\"sourceSeasonId\":\"sea_2026\",\"successorPlanActivationId\":\"pa_1\"}",
                null,
                "{\"titlePattern\":\"Match\",\"timezone\":\"Europe/London\",\"repeat\":\"weekly\",\"count\":3}",
                "{\"eventKeys\":[\"ev_1\"]}",
                null,
                "{\"name\":\"Gold\"}",
                null,
                null,
                "{\"planKey\":\"plan/1\",\"quantity\":2}",
                null,
                "{\"prices\":[{\"category\":\"A\",\"amountMinor\":5000}]}",
                null,
                "{\"eventKey\":\"ev_3\"}",
                null,
                "{\"force\":true}",
                "{\"actionId\":\"ret_1\",\"bookingRef\":\"order_1\",\"planActivationId\":\"pa_1\","
                        + "\"labels\":[\"A-1\"],\"source\":{\"kind\":\"holder\"}}",
                "{\"actionId\":\"rec_1\",\"returnActionId\":\"ret_1\",\"bookingRef\":\"order_1\","
                        + "\"planActivationId\":\"pa_1\",\"labels\":[\"A-1\"],\"source\":{\"kind\":\"holder\"}}",
                null,
                "{\"amountMinor\":500}",
                "{\"reason\":\"customer_request\"}",
                null,
                "{\"email\":\"fan@example.com\"}",
                null,
                null,
                "{\"email\":\"fan@example.com\",\"name\":\"Ann Lee\"}",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "{\"amount\":\"per_date\",\"reason\":\"Storm\"}",
                null,
                null,
                null,
                null,
                "{\"returnUrl\":\"https://tickets.example/renewed\",\"buyerEmail\":\"ana@example.com\","
                        + "\"buyerName\":\"Ana Silva\",\"provider\":\"stripe\"}",
                null);
        assertEquals(39, addedBodies.size());
        for (int offset = 0; offset < addedBodies.size(); offset++) {
            Call call = calls.get(48 + offset);
            String label = "call " + (48 + offset) + " " + expected.get(48 + offset);
            if (addedBodies.get(offset) == null) {
                assertNull(call.body(), label);
            } else {
                assertEquals(Json.read(addedBodies.get(offset)), Json.read(call.body()), label);
            }
        }
        assertTrue(calls.get(77).headers().get("Accept").startsWith("text/csv"));
    }

    @Test
    void priceTypesSaleSettingsHolderContactsAndDateCancelsReachTheWire() {
        List<Call> calls = new ArrayList<>();
        SeatLayer sdk = SeatLayer.builder()
                .secretKey("sk_test_abc")
                .transport((method, url, headers, requestBody) -> {
                    calls.add(new Call(method, url, Map.copyOf(headers), requestBody));
                    return new SeatLayerHttpClient.Response(200, "{}", Map.of());
                })
                .build();
        Seasons seasons = sdk.seasons();
        Map<String, Object> clearReturnCutoff = new LinkedHashMap<>();
        clearReturnCutoff.put("expectedRevision", 3);
        clearReturnCutoff.put("checkoutMode", "seatlayer");
        clearReturnCutoff.put("transferCutoffHours", 24);
        clearReturnCutoff.put("returnCutoffHours", null);

        seasons.replaceSeasonPackagePrices("sea_1", "plan_1", Map.of("prices", List.of(
                Map.of("categoryKey", "stalls", "unitPrice", 18000),
                Map.of("categoryKey", "stalls", "priceTypeKey", "child", "unitPrice", 9000))), null);
        seasons.updateSeason("sea_1", clearReturnCutoff, null);
        seasons.createSeasonHolderImport("sea_1", Map.of(
                "successorPlanActivationId", "pa_1",
                "rows", List.of(Map.of(
                        "rowId", "r1", "holderRef", "h1", "priorPlanActivationId", "pa_0",
                        "priorContractRef", "c1", "labels", List.of("A-1"),
                        "holderEmail", "ana@example.com", "holderName", "Ana Silva"))), null);
        seasons.createSeasonAmendment("sea_1", Map.of(
                "eventKey", "ev_night_3", "kind", "cancel", "refund", "per_date", "reason", "Storm"),
                "night-3-storm");

        List<String> bodies = List.of(
                "{\"prices\":[{\"categoryKey\":\"stalls\",\"unitPrice\":18000},"
                        + "{\"categoryKey\":\"stalls\",\"priceTypeKey\":\"child\",\"unitPrice\":9000}]}",
                "{\"expectedRevision\":3,\"checkoutMode\":\"seatlayer\",\"transferCutoffHours\":24,"
                        + "\"returnCutoffHours\":null}",
                "{\"successorPlanActivationId\":\"pa_1\",\"rows\":[{\"rowId\":\"r1\",\"holderRef\":\"h1\","
                        + "\"priorPlanActivationId\":\"pa_0\",\"priorContractRef\":\"c1\",\"labels\":[\"A-1\"],"
                        + "\"holderEmail\":\"ana@example.com\",\"holderName\":\"Ana Silva\"}]}",
                "{\"eventKey\":\"ev_night_3\",\"kind\":\"cancel\",\"refund\":\"per_date\",\"reason\":\"Storm\"}");
        assertEquals(bodies.size(), calls.size());
        for (int index = 0; index < bodies.size(); index++) {
            assertEquals(Json.read(bodies.get(index)), Json.read(calls.get(index).body()), "call " + index);
            assertTrue(calls.get(index).headers().containsKey("Idempotency-Key"), "call " + index);
        }
        assertTrue(calls.get(1).body().contains("\"returnCutoffHours\":null"));
        assertEquals("night-3-storm", calls.get(3).headers().get("Idempotency-Key"));
    }
}
