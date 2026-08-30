package io.seatlayer;

import static io.seatlayer.SeatLayerHttpClient.body;
import static io.seatlayer.SeatLayerHttpClient.encode;

import java.util.Map;

/** Fixed Renewable Season organizer operations for trusted backends. */
public final class Seasons {

    private final SeatLayerHttpClient http;

    Seasons(SeatLayerHttpClient http) {
        this.http = http;
    }

    private static String path(String seasonKey, String suffix) {
        return "/v1/seasons/" + encode(seasonKey) + suffix;
    }

    public Map<String, Object> listSeasons() {
        return http.get("/v1/seasons");
    }

    public Map<String, Object> listSeasons(
            String workspaceId, String structureState, Integer limit, String cursor) {
        return http.get(
                "/v1/seasons",
                body(
                        "workspaceId", workspaceId,
                        "structureState", structureState,
                        "limit", limit,
                        "cursor", cursor));
    }

    /** Read-only compatibility preflight. */
    public Map<String, Object> validateSeason(Map<String, Object> selection) {
        return http.post("/v1/seasons/validate", selection);
    }

    public Map<String, Object> createSeason(Map<String, Object> params) {
        return createSeason(params, null);
    }

    public Map<String, Object> createSeason(Map<String, Object> params, String idempotencyKey) {
        return http.postWithHeaderReplay("/v1/seasons", params, idempotencyKey);
    }

    public Map<String, Object> retrieveSeason(String seasonKey) {
        return http.get(path(seasonKey, ""));
    }

    public Map<String, Object> updateSeason(
            String seasonKey, Map<String, Object> params, String idempotencyKey) {
        return http.mutationWithHeaderReplay("PATCH", path(seasonKey, ""), params, idempotencyKey);
    }

    public void deleteSeason(String seasonKey, String idempotencyKey) {
        http.mutationWithHeaderReplay("DELETE", path(seasonKey, ""), null, idempotencyKey);
    }

    public Map<String, Object> activateSeason(String seasonKey, int expectedRevision) {
        return http.post(path(seasonKey, "/activate"), body("expectedRevision", expectedRevision));
    }

    public Map<String, Object> closeSeason(String seasonKey, int expectedRevision) {
        return http.post(path(seasonKey, "/close"), body("expectedRevision", expectedRevision));
    }

    public Map<String, Object> archiveSeason(String seasonKey, int expectedRevision) {
        return http.post(path(seasonKey, "/archive"), body("expectedRevision", expectedRevision));
    }

    public Map<String, Object> retrieveSeasonLifecycle(String seasonKey, String operationId) {
        return http.get(path(seasonKey, "/lifecycle/" + encode(operationId)));
    }

    public Map<String, Object> createSeasonPlan(
            String seasonKey, Map<String, Object> params, String idempotencyKey) {
        return http.postWithHeaderReplay(path(seasonKey, "/plans"), params, idempotencyKey);
    }

    public Map<String, Object> retrieveSeasonPlan(String seasonKey, String planKey) {
        return http.get(path(seasonKey, "/plans/" + encode(planKey)));
    }

    public Map<String, Object> publishSeasonPlan(
            String seasonKey, String planKey, int expectedRevision) {
        return http.post(
                path(seasonKey, "/plans/" + encode(planKey) + "/publish"),
                body("expectedRevision", expectedRevision));
    }

    public Map<String, Object> supersedeSeasonPlan(
            String seasonKey, String planKey, int expectedRevision) {
        return http.post(
                path(seasonKey, "/plans/" + encode(planKey) + "/supersede"),
                body("expectedRevision", expectedRevision));
    }

    private Map<String, Object> sales(String seasonKey, String action, int expectedRevision) {
        return http.post(
                path(seasonKey, "/sales/" + action), body("expectedRevision", expectedRevision));
    }

    public Map<String, Object> openSeasonSales(String seasonKey, int expectedRevision) {
        return sales(seasonKey, "open", expectedRevision);
    }

    public Map<String, Object> pauseSeasonSales(String seasonKey, int expectedRevision) {
        return sales(seasonKey, "pause", expectedRevision);
    }

    public Map<String, Object> resumeSeasonSales(String seasonKey, int expectedRevision) {
        return sales(seasonKey, "resume", expectedRevision);
    }

    public Map<String, Object> endSeasonSales(String seasonKey, int expectedRevision) {
        return sales(seasonKey, "end", expectedRevision);
    }

    public Map<String, Object> duplicateSeasonToLive(
            String seasonKey, Map<String, Object> params, String idempotencyKey) {
        return http.postWithHeaderReplay(
                path(seasonKey, "/duplicate-to-live"), params, idempotencyKey);
    }

    /** Reveal one origin-bound browser bearer; this operation remains single-attempt. */
    public Map<String, Object> createSeasonBuyerAccessSession(
            String seasonKey, Map<String, Object> params) {
        return http.post(path(seasonKey, "/buyer-access-sessions"), params);
    }

    public Map<String, Object> listSeasonBuyerAccessSessions(String seasonKey, Integer limit) {
        return http.get(path(seasonKey, "/buyer-access-sessions"), body("limit", limit));
    }

    public Map<String, Object> revokeSeasonBuyerAccessSession(String seasonKey, String sessionId) {
        return http.delete(path(seasonKey, "/buyer-access-sessions/" + encode(sessionId)));
    }

    public Map<String, Object> retrieveSeasonHold(String seasonKey, String operationId) {
        return http.get(path(seasonKey, "/holds/" + encode(operationId)));
    }

    public Map<String, Object> bookSeasonHold(
            String seasonKey, String operationId, String bookActionId, String bookingRef) {
        return http.post(
                path(seasonKey, "/holds/" + encode(operationId) + "/book"),
                body("bookActionId", bookActionId, "bookingRef", bookingRef));
    }

    public Map<String, Object> retrieveSeasonBooking(String seasonKey, String actionId) {
        return http.get(path(seasonKey, "/bookings/" + encode(actionId)));
    }

    public Map<String, Object> cancelSeasonBooking(
            String seasonKey,
            String actionId,
            String cancelActionId,
            String bookingRef,
            String planActivationId,
            String rightDisposition) {
        return http.post(
                path(seasonKey, "/bookings/" + encode(actionId) + "/cancel"),
                body(
                        "cancelActionId", cancelActionId,
                        "bookingRef", bookingRef,
                        "planActivationId", planActivationId,
                        "rightDisposition", rightDisposition));
    }

    public Map<String, Object> validateSeasonBuyerRehearsal(String seasonKey) {
        return http.post(path(seasonKey, "/buyer-rehearsals/validate"));
    }

    public Map<String, Object> createSeasonHolderImport(
            String seasonKey, Map<String, Object> params, String idempotencyKey) {
        return http.postWithHeaderReplay(path(seasonKey, "/imports"), params, idempotencyKey);
    }

    public Map<String, Object> retrieveSeasonHolderImport(String seasonKey, String importId) {
        return http.get(path(seasonKey, "/imports/" + encode(importId)));
    }

    public Map<String, Object> createSeasonRenewalOffers(
            String seasonKey, Map<String, Object> params, String idempotencyKey) {
        return http.postWithHeaderReplay(
                path(seasonKey, "/renewal-offers"), params, idempotencyKey);
    }

    public Map<String, Object> listSeasonRenewalOffers(String seasonKey) {
        return http.get(path(seasonKey, "/renewal-offers"));
    }

    public Map<String, Object> retrieveSeasonRenewalOffer(String seasonKey, String offerId) {
        return http.get(path(seasonKey, "/renewal-offers/" + encode(offerId)));
    }

    public Map<String, Object> extendSeasonRenewalOffer(
            String seasonKey, String offerId, long deadlineAt) {
        return http.post(
                path(seasonKey, "/renewal-offers/" + encode(offerId) + "/extend"),
                body("deadlineAt", deadlineAt));
    }

    public Map<String, Object> inspectSeasonRenewalOffer(String seasonKey, String offerId) {
        return http.get(path(seasonKey, "/renewal-offers/" + encode(offerId) + "/inspect"));
    }

    public Map<String, Object> commitSeasonRenewalOffer(
            String seasonKey,
            String offerId,
            String commitActionId,
            String orderRef,
            String bookingRef,
            String planActivationId) {
        return http.post(
                path(seasonKey, "/renewal-offers/" + encode(offerId) + "/commit"),
                body(
                        "commitActionId", commitActionId,
                        "orderRef", orderRef,
                        "bookingRef", bookingRef,
                        "planActivationId", planActivationId));
    }

    public Map<String, Object> declineSeasonRenewalOffer(String seasonKey, String offerId) {
        return http.post(
                path(seasonKey, "/renewal-offers/" + encode(offerId) + "/decline"), Map.of());
    }

    public Map<String, Object> releaseSeasonRenewalOffer(String seasonKey, String offerId) {
        return http.post(
                path(seasonKey, "/renewal-offers/" + encode(offerId) + "/release"), Map.of());
    }

    public Map<String, Object> listSeasonOccurrences(String seasonKey) {
        return http.get(path(seasonKey, "/occurrences"));
    }

    public Map<String, Object> createSeasonAmendment(
            String seasonKey, Map<String, Object> params, String idempotencyKey) {
        return http.postWithHeaderReplay(path(seasonKey, "/amendments"), params, idempotencyKey);
    }

    public Map<String, Object> listSeasonAmendments(String seasonKey) {
        return http.get(path(seasonKey, "/amendments"));
    }

    public Map<String, Object> retrieveSeasonAmendment(String seasonKey, String amendmentId) {
        return http.get(path(seasonKey, "/amendments/" + encode(amendmentId)));
    }

    public Map<String, Object> retrieveSeasonReport(String seasonKey) {
        return http.get(path(seasonKey, "/reports"));
    }

    public Map<String, Object> listSeasonOperations(String seasonKey) {
        return http.get(path(seasonKey, "/operations"));
    }

    public Map<String, Object> retrieveSeasonSupportLookup(
            String seasonKey, String bookingRef, String holderRef) {
        return http.get(
                path(seasonKey, "/support-lookups"),
                body("bookingRef", bookingRef, "holderRef", holderRef));
    }

    public Map<String, Object> listSeasonOutbox(String seasonKey) {
        return http.get(path(seasonKey, "/outbox"));
    }

    public Map<String, Object> replaySeasonOutbox(String seasonKey, String occurrenceId) {
        return http.post(
                path(seasonKey, "/outbox/" + encode(occurrenceId) + "/replay"), Map.of());
    }

    public Map<String, Object> listSeasonAudit(String seasonKey) {
        return http.get(path(seasonKey, "/audit"));
    }

    public Map<String, Object> exportSeasonSupportSnapshot(String seasonKey) {
        return http.get(path(seasonKey, "/export"));
    }
}
