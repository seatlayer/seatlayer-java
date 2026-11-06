package io.seatlayer;

import static io.seatlayer.SeatLayerHttpClient.body;
import static io.seatlayer.SeatLayerHttpClient.encode;

import java.util.Map;
import java.util.Objects;

/** Fixed Renewable Season organizer operations for trusted backends. */
public final class Seasons {

    private final SeatLayerHttpClient http;

    Seasons(SeatLayerHttpClient http) {
        this.http = http;
    }

    private static String path(String seasonKey, String suffix) {
        return "/v1/seasons/" + encode(seasonKey) + suffix;
    }

    /**
     * The job voiding a cancelled date's tickets: {@code sbj_void_} and the cancel's
     * {@code amendmentId}. Cancelling a date starts it (a repeated cancel joins it); follow it with
     * {@link #retrieveSeasonBulkJob}. Each Season ticket it voids is told by
     * {@code season.ticket.voided} with reason {@code date_cancelled}.
     *
     * <p>Not an API call: the cancel's answer does not name the job, so this names it.
     *
     * @param amendmentId the {@code amendmentId} of the {@code cancel} amendment
     * @return the {@code date_cancel_void} job id
     */
    public static String dateCancelVoidJobId(String amendmentId) {
        return "sbj_void_" + Objects.requireNonNull(amendmentId, "amendmentId");
    }

    /**
     * The most rows one {@link #createSeasonHolderImport} call takes for a package of
     * {@code dates} dates. Each row is fenced on every date: 100 up to 20 dates, then
     * {@code floor(2300 / (dates + 3))}, 43 at 50. A call with more answers
     * {@code 422 invalid_rows} with {@code maxRows}.
     *
     * <p>Not an API call.
     *
     * @param dates the number of dates in the package (2 to 50)
     * @return the most rows one import call takes, at least 1
     */
    public static int holderImportRowsPerCall(int dates) {
        long perCall = 2300L / (Math.max(1, dates) + 3L);
        return (int) Math.max(1, Math.min(100, perCall));
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

    /**
     * Creates a draft Season.
     *
     * <p>Header replay: the same idempotency key is reused on every retry.
     *
     * @param params the request body: {@code name}, {@code eventKeys} or
     *     {@code sourcePerformanceGroupKeys}, and optionally {@code edition}, {@code mode} and
     *     {@code singleTickets} ({@code true}: each date also sells its own single tickets once
     *     the Season is on sale; the default {@code false} is Season only)
     * @param idempotencyKey a stable key, or {@code null} to generate one
     * @return the decoded response
     */
    public Map<String, Object> createSeason(Map<String, Object> params, String idempotencyKey) {
        return http.postWithHeaderReplay("/v1/seasons", params, idempotencyKey);
    }

    public Map<String, Object> retrieveSeason(String seasonKey) {
        return http.get(path(seasonKey, ""));
    }

    /**
     * Renames a draft Season, or sets how it sells.
     *
     * <p>Header replay: the same idempotency key is reused on every retry. A retry with the first
     * key and the same {@code expectedRevision} is not applied twice.
     *
     * <p>When a {@code season_singles} job could not start, {@code singleTicketDates} carries
     * {@code jobRefused} saying why. The change still stands, and the dates wait in
     * {@code singleTicketsNotOpen} until you send {@code singleTickets: true} again.
     *
     * @param seasonKey the Season key
     * @param params the request body: {@code expectedRevision}, plus any of {@code name},
     *     {@code edition}, {@code checkoutMode} ({@code seatlayer} or {@code server}; it cannot go
     *     back to undecided and is fixed once a live booking exists), {@code transferCutoffHours}
     *     and {@code returnCutoffHours} (hours before a date starts, 0 to 720, or {@code null} for
     *     until it starts), {@code audience} ({@code embed_only} or {@code public}; an ended
     *     Season cannot become public, {@code 409 sales_ended}), and {@code singleTickets} (turned
     *     on while the Season is on sale, each upcoming date's own sales open through its
     *     readiness checks; turned off, they stop)
     * @param idempotencyKey a stable key, or {@code null} to generate one
     * @return the decoded response; a change to {@code singleTickets} names the dates it opened
     *     or stopped in {@code singleTicketDates}
     */
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

    /**
     * Opens sales. The answer's {@code warnings} say what is advised but not required, such as
     * {@code checkout_test_missing} until a checkout test or rehearsal has passed. Refused with
     * {@code 409 plan_not_published} or {@code 409 checkout_mode_not_chosen} until a package is
     * published and a checkout mode is chosen.
     *
     * @param seasonKey the Season key
     * @param expectedRevision the Season revision the caller last read
     * @return the decoded response, with {@code season} and {@code warnings}
     */
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

    /**
     * Retrieves a Season hold. Each seat carries the {@code priceTypeKey} it is held as.
     *
     * @param seasonKey the Season key
     * @param operationId the hold operation id
     * @return the decoded response
     */
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

    /**
     * Imports incumbent holders, as a dry run or for real.
     *
     * <p>A package holds 2 to 50 dates. A call takes at most
     * {@link #holderImportRowsPerCall(int)} rows for the package's dates (100 up to 20 dates, 43
     * at 50); more is refused with {@code 422 invalid_rows}, and the error's {@code body()} carries
     * {@code maxRows}.
     *
     * <p>Header replay: the same idempotency key is reused on every retry.
     *
     * @param seasonKey the Season key
     * @param params the request body: {@code successorPlanActivationId}, {@code rows} and optionally
     *     {@code dryRun}; each row takes {@code rowId}, {@code holderRef},
     *     {@code priorPlanActivationId}, {@code priorContractRef} and {@code labels}, plus optional
     *     {@code existingBookingRef}, {@code holderEmail} and {@code holderName}
     * @param idempotencyKey a stable key, or {@code null} to generate one
     * @return the decoded response
     */
    public Map<String, Object> createSeasonHolderImport(
            String seasonKey, Map<String, Object> params, String idempotencyKey) {
        return http.postWithHeaderReplay(path(seasonKey, "/imports"), params, idempotencyKey);
    }

    public Map<String, Object> retrieveSeasonHolderImport(String seasonKey, String importId) {
        return http.get(path(seasonKey, "/imports/" + encode(importId)));
    }

    /**
     * Makes one renewal offer per eligible prior Contract.
     *
     * <p>For 100 contracts or fewer the offers are made in this call: {@code 201} with the
     * {@code offers}. For more than 100, a {@code renewal_run} job makes them: {@code 202} with the
     * {@code job} and its {@code batchId}. Follow the job with {@link #retrieveSeasonBulkJob},
     * then read the offers with {@link #listSeasonRenewalOffers(String, String, Integer)}. Can
     * refuse with {@code 409 job_mismatch} or {@code 503 season_not_configured}.
     *
     * <p>Header replay: the same idempotency key is reused on every retry and answers the same job.
     *
     * @param seasonKey the Season key
     * @param params the request body: {@code deadlineAt}, and optionally
     *     {@code successorPlanActivationId} and {@code contractIds}
     * @param idempotencyKey a stable key, or {@code null} to generate one
     * @return the decoded response: the {@code offers}, or the {@code job} and {@code batchId}
     */
    public Map<String, Object> createSeasonRenewalOffers(
            String seasonKey, Map<String, Object> params, String idempotencyKey) {
        return http.postWithHeaderReplay(
                path(seasonKey, "/renewal-offers"), params, idempotencyKey);
    }

    /**
     * Lists the first page of renewal offers, newest first.
     *
     * @param seasonKey the Season key
     * @return the decoded response, with the {@code offers} and {@code nextCursor}
     */
    public Map<String, Object> listSeasonRenewalOffers(String seasonKey) {
        return listSeasonRenewalOffers(seasonKey, null, null);
    }

    /**
     * Lists renewal offers, one page at a time, newest first. Null arguments are not sent.
     *
     * @param seasonKey the Season key
     * @param cursor the {@code nextCursor} from the previous page, or {@code null}
     * @param limit offers a page, 1-100, or {@code null} for 100
     * @return the decoded response, with the {@code offers} and {@code nextCursor} (null on the
     *     last page)
     */
    public Map<String, Object> listSeasonRenewalOffers(String seasonKey, String cursor, Integer limit) {
        return http.get(path(seasonKey, "/renewal-offers"), body("cursor", cursor, "limit", limit));
    }

    /**
     * Retrieves a renewal offer, with its {@code seats} and the {@code priceTypeKey} each renews at.
     *
     * @param seasonKey the Season key
     * @param offerId the renewal offer id
     * @return the decoded response
     */
    public Map<String, Object> retrieveSeasonRenewalOffer(String seasonKey, String offerId) {
        return http.get(path(seasonKey, "/renewal-offers/" + encode(offerId)));
    }

    public Map<String, Object> extendSeasonRenewalOffer(
            String seasonKey, String offerId, long deadlineAt) {
        return http.post(
                path(seasonKey, "/renewal-offers/" + encode(offerId) + "/extend"),
                body("deadlineAt", deadlineAt));
    }

    /**
     * Inspects a renewal offer, with its {@code seats} and the {@code priceTypeKey} each renews at.
     *
     * @param seasonKey the Season key
     * @param offerId the renewal offer id
     * @return the decoded response
     */
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

    /**
     * Declines a renewal offer and releases its seats at once, with no reason.
     *
     * <p>Single attempt; a repeated decline answers the same offer.
     *
     * @param seasonKey the Season key
     * @param offerId the renewal offer id
     * @return the decoded response
     */
    public Map<String, Object> declineSeasonRenewalOffer(String seasonKey, String offerId) {
        return declineSeasonRenewalOffer(seasonKey, offerId, null);
    }

    /**
     * Declines a renewal offer and releases its seats at once.
     *
     * <p>Single attempt; a repeated decline answers the same offer and keeps the first reason.
     *
     * @param seasonKey the Season key
     * @param offerId the renewal offer id
     * @param params the optional request body ({@code reason}, 1 to 200 characters, kept on the
     *     offer as {@code declineReason}), or {@code null} for none
     * @return the decoded response
     */
    public Map<String, Object> declineSeasonRenewalOffer(
            String seasonKey, String offerId, Map<String, Object> params) {
        return http.post(
                path(seasonKey, "/renewal-offers/" + encode(offerId) + "/decline"),
                params == null ? Map.of() : params);
    }

    public Map<String, Object> releaseSeasonRenewalOffer(String seasonKey, String offerId) {
        return http.post(
                path(seasonKey, "/renewal-offers/" + encode(offerId) + "/release"), Map.of());
    }

    public Map<String, Object> listSeasonOccurrences(String seasonKey) {
        return http.get(path(seasonKey, "/occurrences"));
    }

    /**
     * Amends one date of a published Season.
     *
     * <p>A {@code cancel} also voids every live ticket for the date as a {@code date_cancel_void}
     * job, {@link #dateCancelVoidJobId(String)} of the answer's {@code amendmentId}; follow it with
     * {@link #retrieveSeasonBulkJob}. Each Season ticket it voids is told by
     * {@code season.ticket.voided} with reason {@code date_cancelled}.
     *
     * <p>Header replay: the same idempotency key is reused on every retry.
     *
     * @param seasonKey the Season key
     * @param params the request body: {@code eventKey} and {@code kind} ({@code reschedule},
     *     {@code replace}, {@code cancel_exception}, {@code postpone} or {@code cancel}), plus
     *     optional {@code startsAt}, {@code name}, {@code planActivationId}, and, with
     *     {@code cancel} only, {@code refund} ({@code none} or {@code per_date}, each order's
     *     share of the date; a whole-order refund is the order refund) and {@code reason}; a
     *     refunding cancel answers with its {@code refundJob}
     * @param idempotencyKey a stable key, or {@code null} to generate one
     * @return the decoded response
     */
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

    /**
     * Dry-runs or commits renewal rights derived from a previous Season's holders.
     *
     * <p>A dry run answers {@code 200} and a commit of 100 holders or fewer {@code 201}, both with
     * the {@code import}. A commit of more than 100 holders answers {@code 202} with the
     * {@code job}: a {@code holder_import} job brings them across. Follow it with
     * {@link #retrieveSeasonBulkJob}; its {@code summary} has the same counts when it completes.
     * Can refuse with {@code 409 job_mismatch} or {@code 503 job_busy} or
     * {@code season_not_configured}.
     *
     * <p>Header replay: the same idempotency key is reused on every retry and answers the same job.
     *
     * @param seasonKey the Season key
     * @param params the request body: {@code sourceSeasonId} and {@code successorPlanActivationId},
     *     and optionally {@code dryRun}
     * @param idempotencyKey a stable key, or {@code null} to generate one
     * @return the decoded response: the {@code import}, or the {@code job}
     */
    public Map<String, Object> createSeasonHolderImportFromSeason(
            String seasonKey, Map<String, Object> params, String idempotencyKey) {
        return http.postWithHeaderReplay(
                path(seasonKey, "/imports/from-season"), params, idempotencyKey);
    }

    /**
     * Lists the Season's performances and their package membership, in every lifecycle state.
     *
     * @param seasonKey the Season key
     * @return the decoded response
     */
    public Map<String, Object> listSeasonPerformances(String seasonKey) {
        return http.get(path(seasonKey, "/performances"));
    }

    /**
     * Creates performances inside a draft Season from a repeat rule.
     *
     * <p>Header replay: the same idempotency key is reused on every retry.
     *
     * @param seasonKey the Season key
     * @param params the request body: {@code titlePattern}, {@code timezone} and {@code repeat},
     *     plus any optional fields
     * @param idempotencyKey a stable key, or {@code null} to generate one
     * @return the decoded response
     */
    public Map<String, Object> createSeasonPerformances(
            String seasonKey, Map<String, Object> params, String idempotencyKey) {
        return http.postWithHeaderReplay(path(seasonKey, "/performances"), params, idempotencyKey);
    }

    /**
     * Attaches existing Events to a draft Season and its packages.
     *
     * <p>Header replay: the same idempotency key is reused on every retry.
     *
     * @param seasonKey the Season key
     * @param params the request body: {@code eventKeys}, and optionally {@code attachToPlanKeys}
     * @param idempotencyKey a stable key, or {@code null} to generate one
     * @return the decoded response
     */
    public Map<String, Object> attachSeasonPerformances(
            String seasonKey, Map<String, Object> params, String idempotencyKey) {
        return http.postWithHeaderReplay(
                path(seasonKey, "/performances/attach"), params, idempotencyKey);
    }

    /**
     * Removes a performance from a draft Season and from every package.
     *
     * <p>Header replay: the same idempotency key is reused on every retry.
     *
     * @param seasonKey the Season key
     * @param eventKey the performance's Event key
     * @param idempotencyKey a stable key, or {@code null} to generate one
     * @return the decoded response
     */
    public Map<String, Object> deleteSeasonPerformance(
            String seasonKey, String eventKey, String idempotencyKey) {
        return http.mutationWithHeaderReplay(
                "DELETE", path(seasonKey, "/performances/" + encode(eventKey)), null, idempotencyKey);
    }

    /**
     * Renames a draft package or changes the performances it covers.
     *
     * <p>Header replay: the same idempotency key is reused on every retry.
     *
     * @param seasonKey the Season key
     * @param planKey the package (Plan) key
     * @param params the request body: {@code name} and/or {@code eventKeys}
     * @param idempotencyKey a stable key, or {@code null} to generate one
     * @return the decoded response
     */
    public Map<String, Object> updateSeasonPlan(
            String seasonKey, String planKey, Map<String, Object> params, String idempotencyKey) {
        return http.mutationWithHeaderReplay(
                "PATCH", path(seasonKey, "/plans/" + encode(planKey)), params, idempotencyKey);
    }

    /**
     * Deletes a draft package that is not the Season's last one. The API answers 204 No Content.
     *
     * <p>Header replay: the same idempotency key is reused on every retry.
     *
     * @param seasonKey the Season key
     * @param planKey the package (Plan) key
     * @param idempotencyKey a stable key, or {@code null} to generate one
     */
    public void deleteSeasonPlan(String seasonKey, String planKey, String idempotencyKey) {
        http.mutationWithHeaderReplay(
                "DELETE", path(seasonKey, "/plans/" + encode(planKey)), null, idempotencyKey);
    }

    /**
     * Activates the Season structure and publishes every draft package in one retryable call.
     *
     * <p>Header replay: the same idempotency key is reused on every retry.
     *
     * @param seasonKey the Season key
     * @param idempotencyKey a stable key, or {@code null} to generate one
     * @return the decoded response
     */
    public Map<String, Object> publishSeason(String seasonKey, String idempotencyKey) {
        return http.postWithHeaderReplay(path(seasonKey, "/publish"), null, idempotencyKey);
    }

    /**
     * Runs a server-side hold and release that proves the Season buyer path, with default options.
     *
     * <p>Header replay: the same idempotency key is reused on every retry.
     *
     * @param seasonKey the Season key
     * @param idempotencyKey a stable key, or {@code null} to generate one
     * @return the decoded response
     */
    public Map<String, Object> createSeasonCheckoutTest(String seasonKey, String idempotencyKey) {
        return createSeasonCheckoutTest(seasonKey, null, idempotencyKey);
    }

    /**
     * Runs a server-side hold and release that proves the Season buyer path.
     *
     * <p>Header replay: the same idempotency key is reused on every retry.
     *
     * @param seasonKey the Season key
     * @param params the optional request body ({@code planKey}, {@code quantity}), or {@code null}
     * @param idempotencyKey a stable key, or {@code null} to generate one
     * @return the decoded response
     */
    public Map<String, Object> createSeasonCheckoutTest(
            String seasonKey, Map<String, Object> params, String idempotencyKey) {
        return http.postWithHeaderReplay(path(seasonKey, "/checkout-tests"), params, idempotencyKey);
    }

    /**
     * Lists recent Season checkout tests, newest first.
     *
     * @param seasonKey the Season key
     * @return the decoded response
     */
    public Map<String, Object> listSeasonCheckoutTests(String seasonKey) {
        return http.get(path(seasonKey, "/checkout-tests"));
    }

    /**
     * Replaces a package's price table, per chart category, in minor units.
     *
     * <p>Header replay: the same idempotency key is reused on every retry.
     *
     * @param seasonKey the Season key
     * @param planKey the package (Plan) key
     * @param params the request body: {@code prices}, and optionally {@code currency}; each price
     *     takes {@code categoryKey}, {@code unitPrice} and an optional {@code priceTypeKey}
     *     ({@code adult}, the default, {@code child}, {@code concession}, {@code senior} or
     *     {@code student})
     * @param idempotencyKey a stable key, or {@code null} to generate one
     * @return the decoded response
     */
    public Map<String, Object> replaceSeasonPackagePrices(
            String seasonKey, String planKey, Map<String, Object> params, String idempotencyKey) {
        return http.mutationWithHeaderReplay(
                "PUT", path(seasonKey, "/plans/" + encode(planKey) + "/prices"), params, idempotencyKey);
    }

    /**
     * Lists every package price table for the Season. Each price carries its
     * {@code priceTypeKey} and {@code priceTypeLabel}.
     *
     * @param seasonKey the Season key
     * @return the decoded response
     */
    public Map<String, Object> listSeasonPrices(String seasonKey) {
        return http.get(path(seasonKey, "/prices"));
    }

    /**
     * Adds a performance to a published package and extends its allocations.
     *
     * <p>Header replay: the same idempotency key is reused on every retry.
     *
     * @param seasonKey the Season key
     * @param planKey the package (Plan) key
     * @param params the request body: {@code eventKey}
     * @param idempotencyKey a stable key, or {@code null} to generate one
     * @return the decoded response
     */
    public Map<String, Object> attachSeasonPlanPerformance(
            String seasonKey, String planKey, Map<String, Object> params, String idempotencyKey) {
        return http.postWithHeaderReplay(
                path(seasonKey, "/plans/" + encode(planKey) + "/performances"), params, idempotencyKey);
    }

    /**
     * Removes a performance from a published package and releases its allocations.
     *
     * <p>Header replay: the same idempotency key is reused on every retry.
     *
     * @param seasonKey the Season key
     * @param planKey the package (Plan) key
     * @param eventKey the performance's Event key
     * @param idempotencyKey a stable key, or {@code null} to generate one
     * @return the decoded response
     */
    public Map<String, Object> deleteSeasonPlanPerformance(
            String seasonKey, String planKey, String eventKey, String idempotencyKey) {
        return http.mutationWithHeaderReplay(
                "DELETE",
                path(seasonKey, "/plans/" + encode(planKey) + "/performances/" + encode(eventKey)),
                null,
                idempotencyKey);
    }

    /**
     * Marks a performance past so it stops blocking new Season sales.
     *
     * <p>Header replay: the same idempotency key is reused on every retry.
     *
     * @param seasonKey the Season key
     * @param eventKey the performance's Event key
     * @param params the request body, which may be empty or carry {@code force}
     * @param idempotencyKey a stable key, or {@code null} to generate one
     * @return the decoded response
     */
    public Map<String, Object> markSeasonPerformancePast(
            String seasonKey, String eventKey, Map<String, Object> params, String idempotencyKey) {
        return http.postWithHeaderReplay(
                path(seasonKey, "/performances/" + encode(eventKey) + "/past"), params, idempotencyKey);
    }

    /**
     * Returns one Season occurrence allocation to ordinary Event inventory.
     *
     * <p>Single attempt: the server makes a repeat with the same {@code actionId} exact, so
     * reconcile an unknown outcome by sending the same body again yourself.
     *
     * @param seasonKey the Season key
     * @param eventKey the occurrence's Event key
     * @param params the request body: {@code actionId}, {@code bookingRef},
     *     {@code planActivationId}, {@code labels} and {@code source}
     * @return the decoded response
     */
    public Map<String, Object> returnSeasonOccurrenceInventory(
            String seasonKey, String eventKey, Map<String, Object> params) {
        return http.post(path(seasonKey, "/occurrences/" + encode(eventKey) + "/returns"), params);
    }

    /**
     * Reclaims returned occurrence inventory while it is still free.
     *
     * <p>Single attempt: the server makes a repeat with the same {@code actionId} exact.
     *
     * @param seasonKey the Season key
     * @param eventKey the occurrence's Event key
     * @param params the request body: {@code actionId}, {@code returnActionId},
     *     {@code bookingRef}, {@code planActivationId}, {@code labels} and {@code source}
     * @return the decoded response
     */
    public Map<String, Object> reclaimSeasonOccurrenceInventory(
            String seasonKey, String eventKey, Map<String, Object> params) {
        return http.post(path(seasonKey, "/occurrences/" + encode(eventKey) + "/reclaims"), params);
    }

    /**
     * Lists the first page of Season ticket holders.
     *
     * @param seasonKey the Season key
     * @return the decoded response
     */
    public Map<String, Object> listSeasonHolders(String seasonKey) {
        return listSeasonHolders(seasonKey, null, null, null, null);
    }

    /**
     * Lists Season ticket holders, one page at a time. Null filters are not sent.
     *
     * @param seasonKey the Season key
     * @param q search by holder name, email, reference, order number or seat, or {@code null}
     * @param status only holders in this state, or {@code null}
     * @param cursor the cursor from the previous page, or {@code null}
     * @param limit the page size, or {@code null} for the server default
     * @return the decoded response
     */
    public Map<String, Object> listSeasonHolders(
            String seasonKey, String q, String status, String cursor, Integer limit) {
        return http.get(
                path(seasonKey, "/holders"),
                body("q", q, "status", status, "cursor", cursor, "limit", limit));
    }

    /**
     * Refunds a Season order in full through its payment provider.
     *
     * <p>Header replay: the same idempotency key is reused on every retry.
     *
     * @param seasonKey the Season key
     * @param orderId the order id
     * @param idempotencyKey a stable key, or {@code null} to generate one
     * @return the decoded response
     */
    public Map<String, Object> refundSeasonOrder(
            String seasonKey, String orderId, String idempotencyKey) {
        return refundSeasonOrder(seasonKey, orderId, null, idempotencyKey);
    }

    /**
     * Refunds a Season order in full or in part through its payment provider.
     *
     * <p>Header replay: the same idempotency key is reused on every retry.
     *
     * @param seasonKey the Season key
     * @param orderId the order id
     * @param params the optional request body ({@code amountMinor}), or {@code null} for a full refund
     * @param idempotencyKey a stable key, or {@code null} to generate one
     * @return the decoded response
     */
    public Map<String, Object> refundSeasonOrder(
            String seasonKey, String orderId, Map<String, Object> params, String idempotencyKey) {
        return http.postWithHeaderReplay(
                path(seasonKey, "/orders/" + encode(orderId) + "/refund"), params, idempotencyKey);
    }

    /**
     * Cancels a Season order without moving money and releases every date.
     *
     * <p>Header replay: the same idempotency key is reused on every retry.
     *
     * @param seasonKey the Season key
     * @param orderId the order id
     * @param params the request body: {@code reason}, plus any optional fields
     * @param idempotencyKey a stable key, or {@code null} to generate one
     * @return the decoded response
     */
    public Map<String, Object> cancelSeasonOrder(
            String seasonKey, String orderId, Map<String, Object> params, String idempotencyKey) {
        return http.postWithHeaderReplay(
                path(seasonKey, "/orders/" + encode(orderId) + "/cancel"), params, idempotencyKey);
    }

    /**
     * Takes back one date of a Season order and puts its seats back on sale.
     *
     * <p>Header replay: the same idempotency key is reused on every retry.
     *
     * @param seasonKey the Season key
     * @param orderId the order id
     * @param eventKey the date's Event key
     * @param idempotencyKey a stable key, or {@code null} to generate one
     * @return the decoded response
     */
    public Map<String, Object> cancelSeasonOrderDate(
            String seasonKey, String orderId, String eventKey, String idempotencyKey) {
        return cancelSeasonOrderDate(seasonKey, orderId, eventKey, null, idempotencyKey);
    }

    /**
     * Takes back one date of a Season order and puts its seats back on sale.
     *
     * <p>Header replay: the same idempotency key is reused on every retry.
     *
     * @param seasonKey the Season key
     * @param orderId the order id
     * @param eventKey the date's Event key
     * @param params the optional request body ({@code actionId}), or {@code null}
     * @param idempotencyKey a stable key, or {@code null} to generate one
     * @return the decoded response
     */
    public Map<String, Object> cancelSeasonOrderDate(
            String seasonKey,
            String orderId,
            String eventKey,
            Map<String, Object> params,
            String idempotencyKey) {
        return http.postWithHeaderReplay(
                path(seasonKey, "/orders/" + encode(orderId) + "/dates/" + encode(eventKey) + "/cancel"),
                params,
                idempotencyKey);
    }

    /**
     * Emails a Season order's tickets again, to the address on the order.
     *
     * <p>Single attempt, never retried automatically.
     *
     * @param seasonKey the Season key
     * @param orderId the order id
     * @return the decoded response
     */
    public Map<String, Object> resendSeasonOrderTickets(String seasonKey, String orderId) {
        return resendSeasonOrderTickets(seasonKey, orderId, null);
    }

    /**
     * Emails a Season order's tickets again.
     *
     * <p>Single attempt, never retried automatically.
     *
     * @param seasonKey the Season key
     * @param orderId the order id
     * @param params the optional request body ({@code email}), or {@code null}
     * @return the decoded response
     */
    public Map<String, Object> resendSeasonOrderTickets(
            String seasonKey, String orderId, Map<String, Object> params) {
        return http.post(path(seasonKey, "/orders/" + encode(orderId) + "/resend"), params);
    }

    /**
     * Retrieves hosted renewal links, payments and emails for the first page of offers.
     *
     * @param seasonKey the Season key
     * @return the decoded response, with the {@code offers} and {@code nextCursor}
     */
    public Map<String, Object> retrieveSeasonHostedRenewals(String seasonKey) {
        return retrieveSeasonHostedRenewals(seasonKey, null, null);
    }

    /**
     * Retrieves hosted renewal links, payments and emails per offer, one page at a time, newest
     * first. Null arguments are not sent.
     *
     * @param seasonKey the Season key
     * @param cursor the {@code nextCursor} from the previous page, or {@code null}
     * @param limit offers a page, 1-500, or {@code null} for 500
     * @return the decoded response, with the {@code offers} and {@code nextCursor} (null on the
     *     last page)
     */
    public Map<String, Object> retrieveSeasonHostedRenewals(String seasonKey, String cursor, Integer limit) {
        return http.get(path(seasonKey, "/renewal-hosted"), body("cursor", cursor, "limit", limit));
    }

    /**
     * Makes a new renewal link for one holder; the old link stops working.
     *
     * <p>Single attempt, never retried automatically: the new link is shown once.
     *
     * @param seasonKey the Season key
     * @param offerId the renewal offer id
     * @return the decoded response
     */
    public Map<String, Object> rotateSeasonRenewalOfferLink(String seasonKey, String offerId) {
        return http.post(path(seasonKey, "/renewal-offers/" + encode(offerId) + "/link"));
    }

    /**
     * Sets the name and email a renewal offer is sent to.
     *
     * <p>Single attempt; the server applies a repeat of the same contact exactly once.
     *
     * @param seasonKey the Season key
     * @param offerId the renewal offer id
     * @param params the request body: {@code email} and/or {@code name}
     * @return the decoded response
     */
    public Map<String, Object> updateSeasonRenewalOfferContact(
            String seasonKey, String offerId, Map<String, Object> params) {
        return http.post(
                path(seasonKey, "/renewal-offers/" + encode(offerId) + "/contact"), params);
    }

    /**
     * Emails renewal invitations now, and reminders before the deadline, for every offer.
     *
     * @param seasonKey the Season key
     * @return the decoded response
     */
    public Map<String, Object> createSeasonRenewalNotices(String seasonKey) {
        return createSeasonRenewalNotices(seasonKey, null);
    }

    /**
     * Emails renewal invitations now, and reminders before the deadline.
     *
     * <p>Single attempt; the server does not send the same notice twice.
     *
     * @param seasonKey the Season key
     * @param params the optional request body ({@code offerIds}, at most 20,000; more is refused
     *     with {@code 422 too_many_offer_ids}; or instead {@code batchId}, every open offer one
     *     {@code createSeasonRenewalOffers} batch made), or {@code null} for every open offer
     * @return the decoded response
     */
    public Map<String, Object> createSeasonRenewalNotices(
            String seasonKey, Map<String, Object> params) {
        return http.post(path(seasonKey, "/renewal-notices"), params);
    }

    /**
     * Sends one holder a new renewal invitation.
     *
     * <p>Single attempt, never retried automatically.
     *
     * @param seasonKey the Season key
     * @param offerId the renewal offer id
     * @return the decoded response
     */
    public Map<String, Object> resendSeasonRenewalOffer(String seasonKey, String offerId) {
        return http.post(path(seasonKey, "/renewal-offers/" + encode(offerId) + "/resend"));
    }

    /**
     * Retrieves what renewing an offer costs, priced as the SeatLayer checkout will charge it.
     *
     * <p>{@code problem} is null when SeatLayer checkout can take the renewal now; otherwise it
     * says why not.
     *
     * @param seasonKey the Season key
     * @param offerId the renewal offer id
     * @return the decoded response
     */
    public Map<String, Object> retrieveSeasonRenewalQuote(String seasonKey, String offerId) {
        return http.get(path(seasonKey, "/renewal-offers/" + encode(offerId) + "/quote"));
    }

    /**
     * Records that the holder means to renew, for a renewal taken by phone or at the box office,
     * so your own checkout can commit the offer.
     *
     * <p>Single attempt; a repeat answers the same offer.
     *
     * @param seasonKey the Season key
     * @param offerId the renewal offer id
     * @return the decoded response
     */
    public Map<String, Object> createSeasonRenewalIntent(String seasonKey, String offerId) {
        return http.post(path(seasonKey, "/renewal-offers/" + encode(offerId) + "/intent"));
    }

    /**
     * Starts a SeatLayer checkout that renews an offer, with no hosted page. Send the buyer to
     * {@code payment.url}; paying renews the offer.
     *
     * <p>Header replay: the same idempotency key is reused on every retry and answers the same
     * order.
     *
     * @param seasonKey the Season key
     * @param offerId the renewal offer id
     * @param params the request body: {@code returnUrl} (on one of the organization's allowed
     *     domains), plus optional {@code buyerEmail}, {@code buyerName} and {@code provider}
     * @param idempotencyKey a stable key, or {@code null} to generate one
     * @return the decoded response
     */
    public Map<String, Object> createSeasonRenewalCheckout(
            String seasonKey, String offerId, Map<String, Object> params, String idempotencyKey) {
        return http.postWithHeaderReplay(
                path(seasonKey, "/renewal-offers/" + encode(offerId) + "/checkout"), params, idempotencyKey);
    }

    /**
     * Retrieves Season money, seats, holders and renewals as totals.
     *
     * @param seasonKey the Season key
     * @return the decoded response
     */
    public Map<String, Object> retrieveSeasonSalesReport(String seasonKey) {
        return http.get(path(seasonKey, "/reports/sales"));
    }

    /**
     * Retrieves the seats still free per package and per date.
     *
     * @param seasonKey the Season key
     * @return the decoded response
     */
    public Map<String, Object> retrieveSeasonSeatsReport(String seasonKey) {
        return http.get(path(seasonKey, "/reports/seats"));
    }

    /**
     * Downloads every Season ticket holder as CSV.
     *
     * @param seasonKey the Season key
     * @return the CSV text, exactly as the API sent it
     */
    public String exportSeasonHoldersCsv(String seasonKey) {
        return exportSeasonHoldersCsv(seasonKey, null, null);
    }

    /**
     * Downloads the matching Season ticket holders as CSV. Null filters are not sent.
     *
     * @param seasonKey the Season key
     * @param q search by holder name, email, reference, order number or seat, or {@code null}
     * @param status only holders in this state, or {@code null}
     * @return the CSV text, exactly as the API sent it
     */
    public String exportSeasonHoldersCsv(String seasonKey, String q, String status) {
        return http.getText(path(seasonKey, "/reports/holders.csv"), body("q", q, "status", status));
    }

    /**
     * Downloads every Season order SeatLayer took as CSV.
     *
     * @param seasonKey the Season key
     * @return the CSV text, exactly as the API sent it
     */
    public String exportSeasonOrdersCsv(String seasonKey) {
        return http.getText(path(seasonKey, "/reports/orders.csv"), null);
    }

    /**
     * Retrieves Season seats sold per package and per date.
     *
     * @param seasonKey the Season key
     * @return the decoded response
     */
    public Map<String, Object> retrieveSeasonSeatCounts(String seasonKey) {
        return http.get(path(seasonKey, "/seat-counts"));
    }

    /**
     * Refunds every SeatLayer order holding a cancelled date, as a job. Follow it with
     * {@link #retrieveSeasonBulkJob}.
     *
     * <p>Header replay: the same idempotency key is reused on every retry and replays the same job.
     *
     * @param seasonKey the Season key
     * @param eventKey the cancelled date's Event key
     * @param params the request body: {@code amount} ({@code per_date}, the date's share of each
     *     order; {@code full} is refused with {@code 422 date_refund_per_date_only}, so refund a
     *     whole order with the order refund) and optionally {@code reason}
     * @param idempotencyKey a stable key, or {@code null} to generate one
     * @return the decoded response, with the {@code job}
     */
    public Map<String, Object> refundSeasonPerformance(
            String seasonKey, String eventKey, Map<String, Object> params, String idempotencyKey) {
        return http.postWithHeaderReplay(
                path(seasonKey, "/performances/" + encode(eventKey) + "/refunds"), params, idempotencyKey);
    }

    /**
     * Releases the seats of every lapsed renewal offer, as a job. An offer extended before its
     * turn is skipped and keeps its seat. Follow it with {@link #retrieveSeasonBulkJob}.
     *
     * <p>Header replay: the same idempotency key is reused on every retry and replays the same job.
     *
     * @param seasonKey the Season key
     * @param idempotencyKey a stable key, or {@code null} to generate one
     * @return the decoded response, with the {@code job}
     */
    public Map<String, Object> releaseSeasonLapsedOffers(String seasonKey, String idempotencyKey) {
        return http.postWithHeaderReplay(
                path(seasonKey, "/renewal-offers/release-lapsed"), null, idempotencyKey);
    }

    /**
     * Lists the Season's bulk jobs, newest first (100 at most).
     *
     * @param seasonKey the Season key
     * @return the decoded response, with the {@code jobs}
     */
    public Map<String, Object> listSeasonBulkJobs(String seasonKey) {
        return http.get(path(seasonKey, "/jobs"));
    }

    /**
     * Retrieves one bulk job with its counts, its {@code summary} (empty until it completes) and
     * up to 500 failed items.
     *
     * @param seasonKey the Season key
     * @param jobId the job id
     * @return the decoded response, with the {@code job}
     */
    public Map<String, Object> retrieveSeasonBulkJob(String seasonKey, String jobId) {
        return http.get(path(seasonKey, "/jobs/" + encode(jobId)));
    }
}
