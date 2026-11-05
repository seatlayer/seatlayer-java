package io.seatlayer;

import static io.seatlayer.SeatLayerHttpClient.body;
import static io.seatlayer.SeatLayerHttpClient.encode;

import java.util.Map;

/**
 * Season ticket holders: their customer record, sign-in sessions, seasons, tickets, ticket
 * transfers and returns, plus the customer list.
 *
 * <p>A holder is addressed by the holder reference its imports and bookings carry.
 */
public final class Holders {

    private final SeatLayerHttpClient http;

    Holders(SeatLayerHttpClient http) {
        this.http = http;
    }

    private static String path(String holderRef, String suffix) {
        return "/v1/holders/" + encode(holderRef) + suffix;
    }

    /**
     * Lists the first page of customers.
     *
     * @return the decoded response
     */
    public Map<String, Object> listCustomers() {
        return listCustomers(null, null, null, null);
    }

    /**
     * Lists or finds customers by email or holder reference. Null filters are not sent.
     *
     * @param email only the customer with this email, or {@code null}
     * @param holderRef only the customer linked to this holder reference, or {@code null}
     * @param cursor the cursor from the previous page, or {@code null}
     * @param limit the page size, or {@code null} for the server default
     * @return the decoded response
     */
    public Map<String, Object> listCustomers(
            String email, String holderRef, String cursor, Integer limit) {
        return http.get(
                "/v1/customers",
                body("email", email, "holderRef", holderRef, "cursor", cursor, "limit", limit));
    }

    /**
     * Retrieves the customer behind a holder reference.
     *
     * @param holderRef the holder reference
     * @return the decoded response
     */
    public Map<String, Object> retrieveHolder(String holderRef) {
        return http.get(path(holderRef, ""));
    }

    /**
     * Sets a holder's email, name or phone.
     *
     * <p>Header replay: the same idempotency key is reused on every retry.
     *
     * @param holderRef the holder reference
     * @param params the request body: any of {@code email}, {@code name} and {@code phone}
     * @param idempotencyKey a stable key, or {@code null} to generate one
     * @return the decoded response
     */
    public Map<String, Object> updateHolder(
            String holderRef, Map<String, Object> params, String idempotencyKey) {
        return http.mutationWithHeaderReplay("PATCH", path(holderRef, ""), params, idempotencyKey);
    }

    /**
     * Signs the holder's customer out of the ticket site everywhere.
     *
     * @param holderRef the holder reference
     * @return the decoded response
     */
    public Map<String, Object> revokeHolderSessions(String holderRef) {
        return revokeHolderSessions(holderRef, null);
    }

    /**
     * Signs the holder's customer out of the ticket site.
     *
     * <p>Single attempt; signing out twice has the same result as once.
     *
     * @param holderRef the holder reference
     * @param params the optional request body ({@code sessionId} for one session), or
     *     {@code null} for every session
     * @return the decoded response
     */
    public Map<String, Object> revokeHolderSessions(String holderRef, Map<String, Object> params) {
        return http.post(path(holderRef, "/sessions/revoke"), params);
    }

    /**
     * Lists the seasons a holder holds seats in.
     *
     * @param holderRef the holder reference
     * @return the decoded response
     */
    public Map<String, Object> listHolderSeasons(String holderRef) {
        return http.get(path(holderRef, "/seasons"));
    }

    /**
     * Lists the first page of a holder's tickets, date by date.
     *
     * @param holderRef the holder reference
     * @return the decoded response
     */
    public Map<String, Object> listHolderTickets(String holderRef) {
        return listHolderTickets(holderRef, null, null, null, null, null);
    }

    /**
     * Lists a holder's tickets, date by date. Null filters are not sent.
     *
     * @param holderRef the holder reference
     * @param seasonKey only tickets of this Season, or {@code null}
     * @param from only dates starting at or after this time, in epoch milliseconds, or {@code null}
     * @param to only dates starting before this time, in epoch milliseconds, or {@code null}
     * @param cursor the cursor from the previous page, or {@code null}
     * @param limit the page size, or {@code null} for the server default
     * @return the decoded response
     */
    public Map<String, Object> listHolderTickets(
            String holderRef, String seasonKey, Long from, Long to, String cursor, Integer limit) {
        return http.get(
                path(holderRef, "/tickets"),
                body(
                        "seasonKey", seasonKey,
                        "from", from,
                        "to", to,
                        "cursor", cursor,
                        "limit", limit));
    }

    /**
     * Retrieves one of a holder's tickets with its QR.
     *
     * @param holderRef the holder reference
     * @param ticketId the ticket id
     * @return the decoded response
     */
    public Map<String, Object> retrieveHolderTicket(String holderRef, String ticketId) {
        return http.get(path(holderRef, "/tickets/" + encode(ticketId)));
    }

    /**
     * Sends some of a holder's season tickets to an email.
     *
     * <p>Header replay: the same idempotency key is reused on every retry.
     *
     * @param holderRef the holder reference
     * @param params the request body: {@code ticketIds} and {@code toEmail}
     * @param idempotencyKey a stable key, or {@code null} to generate one
     * @return the decoded response
     */
    public Map<String, Object> createHolderTransfer(
            String holderRef, Map<String, Object> params, String idempotencyKey) {
        return http.postWithHeaderReplay(path(holderRef, "/transfers"), params, idempotencyKey);
    }

    /**
     * Lists the transfers a holder sent or was sent.
     *
     * @param holderRef the holder reference
     * @return the decoded response
     */
    public Map<String, Object> listHolderTransfers(String holderRef) {
        return http.get(path(holderRef, "/transfers"));
    }

    /**
     * Cancels a transfer that has not been accepted.
     *
     * <p>Single attempt; the server applies a repeat exactly once.
     *
     * @param holderRef the sending holder's reference
     * @param transferId the transfer id
     * @return the decoded response
     */
    public Map<String, Object> cancelHolderTransfer(String holderRef, String transferId) {
        return http.post(path(holderRef, "/transfers/" + encode(transferId) + "/cancel"));
    }

    /**
     * Accepts a transfer for the recipient holder.
     *
     * <p>Single attempt; the server applies a repeat exactly once.
     *
     * @param holderRef the recipient holder's reference
     * @param transferId the transfer id
     * @return the decoded response
     */
    public Map<String, Object> acceptHolderTransfer(String holderRef, String transferId) {
        return http.post(path(holderRef, "/transfers/" + encode(transferId) + "/accept"));
    }

    /**
     * Gives back a holder's seat on one date, one per ticket.
     *
     * <p>Header replay: the same idempotency key is reused on every retry. A repeat answers with
     * the same return.
     *
     * @param holderRef the holder reference
     * @param params the request body: {@code ticketIds}, 1 to 20 tickets of one season
     * @param idempotencyKey a stable key, or {@code null} to generate one
     * @return the decoded response, with the return under {@code return}
     */
    public Map<String, Object> createHolderReturn(
            String holderRef, Map<String, Object> params, String idempotencyKey) {
        return http.postWithHeaderReplay(path(holderRef, "/returns"), params, idempotencyKey);
    }
}
