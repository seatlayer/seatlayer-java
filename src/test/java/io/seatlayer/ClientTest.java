package io.seatlayer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Client behaviour: auth, idempotency, retry, error mapping, pagination. */
class ClientTest {

    private final List<Recorded> calls = new ArrayList<>();

    private record Recorded(String method, String url, Map<String, String> headers, String body) {
    }

    private record Stub(int status, String body, Map<String, String> headers) {
        static Stub of(int status, String body) {
            return new Stub(status, body, Map.of());
        }
    }

    /** Replay a queue of responses and record every request. */
    private SeatLayer client(List<Stub> responses, int maxRetries) {
        calls.clear();
        Deque<Stub> queue = new ArrayDeque<>(responses);
        return SeatLayer.builder()
                .secretKey("sk_test_abc")
                .maxRetries(maxRetries)
                .transport((method, url, headers, body) -> {
                    calls.add(new Recorded(method, url, Map.copyOf(headers), body));
                    if (queue.isEmpty()) {
                        throw new AssertionError("more requests than queued responses");
                    }
                    Stub next = queue.poll();
                    return new SeatLayerHttpClient.Response(next.status(), next.body(), next.headers());
                })
                .build();
    }

    private SeatLayer client(List<Stub> responses) {
        return client(responses, 3);
    }

    private Recorded call(int index) {
        assertTrue(index < calls.size(), "No request recorded at index " + index);
        return calls.get(index);
    }

    @Nested
    @DisplayName("construction")
    class Construction {

        @Test
        @DisplayName("rejects a publishable key by name")
        void rejectsPublishableKey() {
            // The pk_/sk_ mix-up is the most common first-run failure; a 401 three
            // round-trips later teaches nothing.
            var error = assertThrows(IllegalArgumentException.class, () -> new SeatLayer("pk_test_abc"));
            assertTrue(error.getMessage().contains("publishable key"));
        }

        @Test
        @DisplayName("rejects anything that is not a secret key")
        void rejectsNonSecretKey() {
            assertThrows(IllegalArgumentException.class, () -> new SeatLayer("nonsense"));
            assertThrows(IllegalArgumentException.class, () -> new SeatLayer(""));
            assertThrows(IllegalArgumentException.class, () -> new SeatLayer(null));
        }

        @Test
        @DisplayName("reports the key mode")
        void reportsMode() {
            assertEquals("test", new SeatLayer("sk_test_abc").mode());
            assertEquals("live", new SeatLayer("sk_live_abc").mode());
        }
    }

    @Nested
    @DisplayName("requests")
    class Requests {

        @Test
        @DisplayName("sends bearer auth and parses the body")
        void bearerAuth() {
            SeatLayer sdk = client(List.of(Stub.of(200, "{\"meta\":{\"key\":\"ev_1\"}}")));
            Map<String, Object> result = sdk.events().retrieve("ev_1");

            @SuppressWarnings("unchecked")
            Map<String, Object> meta = (Map<String, Object>) result.get("meta");
            assertEquals("ev_1", meta.get("key"));
            assertEquals("Bearer sk_test_abc", call(0).headers().get("Authorization"));
            assertEquals("https://api.seatlayer.io/v1/events/ev_1", call(0).url());
        }

        @Test
        @DisplayName("percent-encodes path parameters")
        void encodesPathParameters() {
            SeatLayer sdk = client(List.of(Stub.of(200, "{}")));
            sdk.events().retrieve("ev/../admin");
            assertEquals("https://api.seatlayer.io/v1/events/ev%2F..%2Fadmin", call(0).url());
        }

        @Test
        @DisplayName("generates an Idempotency-Key only for header-replay mutations")
        void idempotencyKeyOnlyOnHeaderReplayMutations() {
            SeatLayer sdk = client(List.of(
                    Stub.of(200, "{}"), Stub.of(201, "{}"), Stub.of(200, "{\"holdId\":\"h_1\"}")));
            sdk.events().list();
            sdk.events().create("c_1");
            sdk.inventory().hold("ev_1", List.of("A-1"));

            assertNull(call(0).headers().get("Idempotency-Key"));
            assertTrue(call(1).headers().get("Idempotency-Key").matches("[A-Za-z0-9._:-]{1,128}"));
            assertNull(call(2).headers().get("Idempotency-Key"));
        }

        @Test
        @DisplayName("instantiates templates with an object body and escaped identifier")
        void instantiateTemplateUsesObjectBodyAndEscapedPath() {
            SeatLayer sdk = client(List.of(Stub.of(201, "{\"meta\":{}}")));

            sdk.templates().instantiateTemplate("tpl / main");

            assertEquals("POST", call(0).method());
            assertEquals(
                    "https://api.seatlayer.io/v1/templates/tpl%20%2F%20main/instantiate",
                    call(0).url());
            assertEquals("{}", call(0).body());
            assertTrue(call(0).headers().get("Idempotency-Key").matches("[A-Za-z0-9._:-]{1,128}"));
        }

        @Test
        @DisplayName("sends ticket-release routes and whole-list request body")
        void ticketReleaseRoutesAndBody() {
            SeatLayer sdk = client(List.of(
                    Stub.of(200, "{\"releases\":[]}"),
                    Stub.of(200, "{\"releases\":[]}"),
                    Stub.of(200, "{\"releases\":[]}")));
            Map<String, Object> release = new LinkedHashMap<>();
            release.put("name", "Early bird");
            release.put("price", 18);

            sdk.events().listTicketReleases("ev / main");
            sdk.events().updateTicketReleases("ev / main", List.of(release));
            sdk.events().closeTicketRelease("ev / main", "rel / one");

            assertEquals(
                    "https://api.seatlayer.io/v1/events/ev%20%2F%20main/releases", call(0).url());
            assertEquals("PUT", call(1).method());
            assertEquals(
                    "{\"releases\":[{\"name\":\"Early bird\",\"price\":18}]}", call(1).body());
            assertEquals(
                    "https://api.seatlayer.io/v1/events/ev%20%2F%20main/releases/rel%20%2F%20one/close",
                    call(2).url());
        }

        @Test
        @DisplayName("rejects an idempotency key the API would reject")
        void rejectsBadIdempotencyKey() {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> SeatLayerHttpClient.assertValidIdempotencyKey("has spaces"));
        }

        @Test
        @DisplayName("drops null query parameters instead of sending \"null\"")
        void dropsNullQueryParameters() {
            SeatLayer sdk = client(List.of(Stub.of(200, "{}")));
            sdk.charts().list(ChartListOptions.builder().workspaceId("ws_1").build());
            assertEquals("https://api.seatlayer.io/v1/charts?workspaceId=ws_1", call(0).url());
        }
    }

    @Nested
    @DisplayName("errors")
    class Errors {

        @Test
        @DisplayName("maps 403 mode_mismatch to a typed, self-explaining error")
        void modeMismatch() {
            SeatLayer sdk = client(List.of(Stub.of(403, "{\"error\":\"mode_mismatch\"}")));
            var error = assertThrows(
                    SeatLayerAuthException.class, () -> sdk.events().retrieve("ev_1"));
            assertTrue(error.isModeMismatch());
        }

        @Test
        @DisplayName("exposes conflicts on a 409 so callers can branch per seat")
        void conflicts() {
            SeatLayer sdk = client(List.of(Stub.of(
                    409, "{\"error\":\"conflict\",\"conflicts\":[{\"label\":\"A-1\",\"status\":\"booked\"}]}")));
            var error = assertThrows(
                    SeatLayerConflictException.class, () -> sdk.inventory().hold("ev_1", List.of("A-1")));
            assertEquals(1, error.conflicts().size());
            assertEquals("A-1", error.conflicts().get(0).get("label"));
        }

        @Test
        @DisplayName("flags a sold-out best-available result as a business outcome")
        void soldOut() {
            SeatLayer sdk = client(List.of(Stub.of(409, "{\"error\":\"conflict\",\"reason\":\"sold_out\"}")));
            var error = assertThrows(
                    SeatLayerConflictException.class, () -> sdk.inventory().holdBestAvailable("ev_1", 4));
            assertTrue(error.isSoldOut());
        }

        @Test
        @DisplayName("404 does not confirm another org's resource exists")
        void notFound() {
            SeatLayer sdk = client(List.of(Stub.of(404, "{\"error\":\"not_found\"}")));
            assertThrows(SeatLayerNotFoundException.class, () -> sdk.events().retrieve("ev_1"));
        }

        @Test
        @DisplayName("surfaces the request id for support")
        void requestId() {
            SeatLayer sdk = client(
                    List.of(new Stub(500, "{\"error\":\"internal\"}", Map.of("x-request-id", "req_9"))), 1);
            var error = assertThrows(SeatLayerException.class, () -> sdk.events().retrieve("ev_1"));
            assertEquals("req_9", error.requestId());
        }

        @Test
        @DisplayName("prefers body.code while preserving status, body, and request id")
        void stableErrorContract() {
            SeatLayer sdk = client(
                    List.of(new Stub(
                            422,
                            "{\"error\":\"validation_failed\",\"code\":\"invalid_expiry\",\"field\":\"expiresAt\"}",
                            Map.of("x-request-id", "req_contract"))),
                    1);
            var error = assertThrows(
                    SeatLayerValidationException.class,
                    () -> sdk.channels().createAccessLink(
                            "ev_1", "ch_1", null, null, 1L, null, null, null, null));
            assertEquals(422, error.status());
            assertEquals("invalid_expiry", error.code());
            assertEquals("expiresAt", error.body().get("field"));
            assertEquals("req_contract", error.requestId());
        }

        @Test
        @DisplayName("survives an error body that is not JSON")
        void nonJsonErrorBody() {
            // A proxy or WAF can answer with HTML; that must not become a parse crash
            // that hides the real status from the caller.
            SeatLayer sdk = client(List.of(Stub.of(502, "<html>bad gateway</html>")), 1);
            var error = assertThrows(SeatLayerException.class, () -> sdk.events().retrieve("ev_1"));
            assertEquals(502, error.status());
        }
    }

    @Nested
    @DisplayName("retry")
    class Retry {

        @Test
        @DisplayName("retries a 429 and reuses the same idempotency key")
        void retriesAndReusesKey() {
            SeatLayer sdk = client(List.of(
                    new Stub(429, "{\"error\":\"rate_limited\"}", Map.of("retry-after", "0")),
                    Stub.of(201, "{\"ok\":true}")));
            sdk.events().create("c_1");

            assertEquals(2, calls.size());
            // Same key on the retry, or the server would create two events.
            assertEquals(
                    call(0).headers().get("Idempotency-Key"), call(1).headers().get("Idempotency-Key"));
        }

        @Test
        @DisplayName("template instantiation retries with one stable idempotency key")
        void templateInstantiationRetriesAndReusesKey() {
            SeatLayer sdk = client(List.of(
                    new Stub(429, "{\"error\":\"rate_limited\"}", Map.of("retry-after", "0")),
                    Stub.of(201, "{\"meta\":{}}")));

            sdk.templates().instantiateTemplate("tpl_1");

            assertEquals(2, calls.size());
            assertEquals(
                    call(0).headers().get("Idempotency-Key"), call(1).headers().get("Idempotency-Key"));
        }

        @Test
        @DisplayName("ticket-release mutations are single-attempt")
        void ticketReleaseMutationsAreSingleAttempt() {
            SeatLayer updater = client(List.of(
                    new Stub(429, "{\"error\":\"rate_limited\"}", Map.of("retry-after", "0"))));
            Map<String, Object> release = new LinkedHashMap<>();
            release.put("name", "Early bird");
            release.put("price", 18);
            assertThrows(
                    SeatLayerRateLimitException.class,
                    () -> updater.events().updateTicketReleases("ev_1", List.of(release)));
            assertEquals(1, calls.size());
            assertNull(call(0).headers().get("Idempotency-Key"));

            SeatLayer closer = client(List.of(
                    new Stub(429, "{\"error\":\"rate_limited\"}", Map.of("retry-after", "0"))));
            assertThrows(
                    SeatLayerRateLimitException.class,
                    () -> closer.events().closeTicketRelease("ev_1", "rel_123456789abc"));
            assertEquals(1, calls.size());
            assertNull(call(0).headers().get("Idempotency-Key"));
        }

        @Test
        @DisplayName("read retries remain enabled without an idempotency key")
        void retriesReads() {
            SeatLayer sdk = client(List.of(
                    new Stub(429, "{\"error\":\"rate_limited\"}", Map.of("retry-after", "0")),
                    Stub.of(200, "{\"meta\":{\"key\":\"ev_1\"}}")));
            sdk.events().retrieve("ev_1");

            assertEquals(2, calls.size());
            assertNull(call(0).headers().get("Idempotency-Key"));
            assertNull(call(1).headers().get("Idempotency-Key"));
        }

        @Test
        @DisplayName("booking is single-attempt and does not generate an idempotency key")
        void bookingIsSingleAttempt() {
            SeatLayer sdk = client(List.of(
                    new Stub(429, "{\"error\":\"rate_limited\"}", Map.of("retry-after", "0"))));

            assertThrows(
                    SeatLayerRateLimitException.class,
                    () -> sdk.inventory().bookBestAvailable("ev_1", 2, "order-42"));
            assertEquals(1, calls.size());
            assertNull(call(0).headers().get("Idempotency-Key"));
        }

        @Test
        @DisplayName("a supplied key on unsupported booking is forwarded without enabling retries")
        void unsupportedExplicitKeyDoesNotEnableRetries() {
            SeatLayer sdk = client(List.of(
                    new Stub(429, "{\"error\":\"rate_limited\"}", Map.of("retry-after", "0"))));

            assertThrows(
                    SeatLayerRateLimitException.class,
                    () -> sdk.inventory()
                            .bookBestAvailable("ev_1", 2, "order-42", null, null, "retry-me"));
            assertEquals(1, calls.size());
            assertEquals("retry-me", call(0).headers().get("Idempotency-Key"));
        }

        @Test
        @DisplayName("a raw mutation is single-attempt and has no generated idempotency key")
        void rawMutationIsSingleAttempt() {
            SeatLayer sdk = client(List.of(
                    new Stub(429, "{\"error\":\"rate_limited\"}", Map.of("retry-after", "0"))));

            assertThrows(
                    SeatLayerRateLimitException.class,
                    () -> sdk.request("POST", "/v1/future-mutation", null, Map.of("value", 1)));
            assertEquals(1, calls.size());
            assertNull(call(0).headers().get("Idempotency-Key"));
        }

        @Test
        @DisplayName("does not retry a 4xx that will never succeed")
        void doesNotRetry4xx() {
            SeatLayer sdk = client(List.of(Stub.of(422, "{\"error\":\"invalid_slug\"}")));
            assertThrows(SeatLayerValidationException.class, () -> sdk.events().create("c_1"));
            assertEquals(1, calls.size());
        }

        @Test
        @DisplayName("gives up after maxRetries and throws the last error")
        void givesUp() {
            SeatLayer sdk = client(
                    List.of(
                            new Stub(429, "{}", Map.of("retry-after", "0")),
                            new Stub(429, "{}", Map.of("retry-after", "0"))),
                    2);
            assertThrows(SeatLayerRateLimitException.class, () -> sdk.events().create("c_1"));
            assertEquals(2, calls.size());
        }

        @Test
        @DisplayName("prefers Retry-After over the JSON field")
        void prefersHeader() {
            SeatLayer sdk = client(
                    List.of(new Stub(
                            429,
                            "{\"error\":\"rate_limited\",\"retryAfterSeconds\":99}",
                            Map.of("retry-after", "0"))),
                    1);
            var error = assertThrows(
                    SeatLayerRateLimitException.class, () -> sdk.events().retrieve("ev_1"));
            assertEquals(0.0, error.retryAfterSeconds());
        }
    }

    @Nested
    @DisplayName("pagination")
    class Pagination {

        @Test
        @DisplayName("listAll walks every page and stops when the cursor runs out")
        void listAllWalksPages() {
            SeatLayer sdk = client(List.of(
                    Stub.of(200, "{\"charts\":[{\"id\":\"c_1\"},{\"id\":\"c_2\"}],\"nextCursor\":\"cur_1\"}"),
                    Stub.of(200, "{\"charts\":[{\"id\":\"c_3\"}]}")));

            List<Object> seen = new ArrayList<>();
            for (Map<String, Object> chart : sdk.charts().listAll()) {
                seen.add(chart.get("id"));
            }

            assertEquals(List.of("c_1", "c_2", "c_3"), seen);
            assertEquals(2, calls.size());
            // Absent nextCursor terminates — a caller looping cannot spin forever.
            assertTrue(call(1).url().contains("cursor=cur_1"));
        }

        @Test
        @DisplayName("listAll over events skips the per-event counts fanout")
        void listAllSkipsCounts() {
            // Counts cost a server round-trip PER EVENT, which is exactly the cost
            // pagination was added to avoid.
            SeatLayer sdk = client(List.of(Stub.of(200, "{\"events\":[]}")));
            for (Map<String, Object> ignored : sdk.events().listAll()) {
                // drain
            }
            assertTrue(call(0).url().contains("counts=0"));
        }

        @Test
        @DisplayName("a single explicit page keeps counts")
        void singlePageKeepsCounts() {
            SeatLayer sdk = client(List.of(Stub.of(200, "{\"events\":[]}")));
            sdk.events().list(EventListOptions.builder().limit(10).build());
            assertFalse(call(0).url().contains("counts=0"));
        }

        @Test
        @DisplayName("an empty first page terminates immediately")
        void emptyFirstPage() {
            SeatLayer sdk = client(List.of(Stub.of(200, "{\"charts\":[]}")));
            assertFalse(sdk.charts().listAll().iterator().hasNext());
            assertEquals(1, calls.size());
        }
    }

    @Nested
    @DisplayName("sessions and inventory")
    class SessionsAndInventory {

        @Test
        @DisplayName("refuses to mint a manage session without explicit capabilities")
        void requiresCapabilities() {
            SeatLayer sdk = client(List.of());
            // The API defaults omission to view-only, but the SDK requires an explicit
            // grant so browser authority stays reviewable at the call site.
            var error = assertThrows(
                    IllegalArgumentException.class,
                    () -> sdk.sessions().createManageSession("ev_1", "https://box.example", List.of()));
            assertTrue(error.getMessage().contains("capabilities is required"));
        }

        @Test
        @DisplayName("mints with the capabilities it was given")
        void mintsWithCapabilities() {
            SeatLayer sdk = client(List.of(Stub.of(201, "{\"token\":\"mse_x\"}")));
            sdk.sessions().createManageSession("ev_1", "https://box.example", List.of("event:view"));

            Map<String, Object> body = Json.readObject(call(0).body());
            assertEquals(List.of("event:view"), body.get("capabilities"));
        }

        @Test
        @DisplayName("chart update sends expectedUpdatedAt")
        void chartUpdateSendsExpectedUpdatedAt() {
            SeatLayer sdk = client(List.of(Stub.of(200, "{\"meta\":{}}")));
            sdk.charts().update("c_1", Map.of("version", 1), 1234L);

            Map<String, Object> body = Json.readObject(call(0).body());
            assertEquals(1234L, body.get("expectedUpdatedAt"));
        }

        @Test
        @DisplayName("extendHold posts the hold id to the extend route")
        void extendHold() {
            SeatLayer sdk = client(List.of(Stub.of(200, "{\"ok\":true,\"expiresAt\":123}")));
            sdk.inventory().extendHold("ev_1", "h_9", 600000L);

            assertEquals("https://api.seatlayer.io/v1/events/ev_1/extend", call(0).url());
            Map<String, Object> body = Json.readObject(call(0).body());
            assertEquals("h_9", body.get("holdId"));
            assertEquals(600000L, body.get("ttlMs"));
        }

        @Test
        @DisplayName("a hold carries its channel authority and audit reason")
        void holdCarriesChannelAuthority() {
            SeatLayer sdk = client(List.of(Stub.of(200, "{\"holdId\":\"h_1\"}")));
            sdk.inventory().hold(
                    "ev_1",
                    List.of("A-1"),
                    null,
                    "partner-order-42",
                    List.of("ch_partner"),
                    false,
                    "partner checkout");

            Map<String, Object> body = Json.readObject(call(0).body());
            assertEquals(List.of("ch_partner"), body.get("channelIds"));
            assertEquals(false, body.get("ignoreChannelRestrictions"));
            assertEquals("partner checkout", body.get("reason"));
        }

        @Test
        @DisplayName("extended inventory request shapes retain authority, release time, and nullable TTL")
        void extendedInventoryContracts() {
            SeatLayer sdk = client(List.of(
                    Stub.of(200, "{\"ok\":true}"),
                    Stub.of(200, "{\"ok\":true}"),
                    Stub.of(200, "{\"ok\":true,\"holdTtlMs\":null}")));
            sdk.inventory().extendHold(
                    "ev_1", "h_1", null, List.of("ch_partner"), true, "staff override");
            sdk.inventory().block("ev_1", List.of("A-1"), 1_800_000_000_000L);
            sdk.events().updateHoldTtl("ev_1", null);

            Map<String, Object> extend = Json.readObject(call(0).body());
            assertEquals(List.of("ch_partner"), extend.get("channelIds"));
            assertEquals(true, extend.get("ignoreChannelRestrictions"));
            assertEquals("staff override", extend.get("reason"));
            assertEquals(1_800_000_000_000L, Json.readObject(call(1).body()).get("releaseAt"));
            assertTrue(Json.readObject(call(2).body()).containsKey("holdTtlMs"));
        }

        @Test
        @DisplayName("event, poster, hosted link, designer, and webhook request shapes match the public contract")
        void extendedPublicRequestContracts() {
            SeatLayer sdk = client(List.of(
                    Stub.of(201, "{\"meta\":{}}"),
                    Stub.of(200, "{\"ok\":true,\"updated\":true,\"meta\":{}}"),
                    Stub.of(200, "{\"meta\":{}}"),
                    Stub.of(201, "{\"link\":{},\"capability\":\"x\"}"),
                    Stub.of(201, "{\"session\":{\"id\":\"dse_1\"}}"),
                    Stub.of(200, "{\"deliveries\":[]}"),
                    Stub.of(200, "{\"sessions\":[]}"),
                    Stub.of(200, "{\"ok\":true,\"channel\":{}}")));

            sdk.events().create(Map.of(
                    "chartId", "c_1",
                    "description", "Gala",
                    "endsAt", 1_800_000_000_000L,
                    "timezone", "Europe/London",
                    "locale", "en-GB",
                    "mode", "test"));
            sdk.events().updateChart("ev_1", true, "accept allocation drop");
            sdk.events().updatePoster("ev_1", new byte[] {(byte) 0x89, 'P', 'N', 'G'}, "image/png");
            Map<String, Object> linkResult = sdk.channels().createAccessLink(
                    "ev/1", "ch/1", "Partner", false, null, 50, 4, 900, null);
            Map<String, Object> designerResult = sdk.sessions().createDesignerSession(
                    "ws_1", "c_1", "https://designer.example", "publish", "safe", null,
                    true, Map.of("allowDeletingObjects", false), Map.of("tables", true));
            sdk.webhooks().listDeliveries("wh_1", 25, "failed", 1_800_000_000_000L);
            sdk.channels().listBuyerAccessSessions("ev_1", 20);
            sdk.channels().archive("ev_1", "ch_1", null, "return to public");

            assertEquals("test", Json.readObject(call(0).body()).get("mode"));
            assertEquals(true, Json.readObject(call(1).body()).get("acknowledgeDroppedAssignments"));
            assertEquals("image/png", call(2).headers().get("Content-Type"));
            assertTrue(call(3).url().contains("/events/ev%2F1/channels/ch%2F1/access-links"));
            assertEquals(true, Json.readObject(call(4).body()).get("canPublish"));
            assertEquals("x", linkResult.get("capability"));
            @SuppressWarnings("unchecked")
            Map<String, Object> session = (Map<String, Object>) designerResult.get("session");
            assertEquals("dse_1", session.get("id"));
            assertTrue(call(5).url().contains("status=failed"));
            assertEquals(
                    "https://api.seatlayer.io/v1/events/ev_1/buyer-access-sessions?limit=20",
                    call(6).url());
            assertTrue(Json.readObject(call(7).body()).containsKey("destination"));
            assertEquals(null, Json.readObject(call(7).body()).get("destination"));
        }

        @Test
        @DisplayName("buyer access is origin-bound and carries only requested channels")
        void createsBuyerAccessSession() {
            SeatLayer sdk = client(List.of(Stub.of(201, "{\"token\":\"bas_x\"}")));
            sdk.channels().createBuyerAccessSession(
                    "ev/1",
                    false,
                    "https://partner.example",
                    List.of("ch_1"),
                    null,
                    4,
                    null,
                    null,
                    null,
                    "partner-order-42");

            assertEquals(
                    "https://api.seatlayer.io/v1/events/ev%2F1/buyer-access-sessions",
                    call(0).url());
            assertEquals("partner-order-42", call(0).headers().get("Idempotency-Key"));
            Map<String, Object> body = Json.readObject(call(0).body());
            assertEquals(false, body.get("includePublic"));
            assertEquals("https://partner.example", body.get("allowedOrigin"));
            assertEquals(List.of("ch_1"), body.get("channelIds"));
        }

        @Test
        @DisplayName("booking references are trimmed and encoded as one path segment")
        void retrievesEncodedBookingReference() {
            SeatLayer sdk = client(List.of(Stub.of(200, "{\"bookingRef\":\"order / 42\"}")));
            sdk.inventory().retrieveBooking("ev_1", "  order / 42  ");

            assertEquals(
                    "https://api.seatlayer.io/v1/events/ev_1/bookings/order%20%2F%2042",
                    call(0).url());
        }

        @Test
        @DisplayName("a blank booking reference fails before making a request")
        void rejectsBlankBookingReference() {
            SeatLayer sdk = client(List.of());
            var error = assertThrows(
                    IllegalArgumentException.class,
                    () -> sdk.inventory().unbook("ev_1", List.of("A-1"), "   "));
            assertTrue(error.getMessage().contains("bookingRef is required"));
        }

        @Test
        @DisplayName("a spent hold surfaces as a conflict, not a generic failure")
        void spentHoldIsConflict() {
            SeatLayer sdk = client(List.of(Stub.of(409, "{\"error\":\"cannot_extend\",\"reason\":\"expired\"}")));
            var error = assertThrows(
                    SeatLayerConflictException.class, () -> sdk.inventory().extendHold("ev_1", "h_9"));
            assertEquals("cannot_extend", error.code());
        }
    }
}
