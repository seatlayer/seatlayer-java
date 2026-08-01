package io.seatlayer;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 409 — the seats moved under you.
 *
 * <p>Normal in ticketing, not exceptional: two buyers wanted the same seat and one lost.
 */
public class SeatLayerConflictException extends SeatLayerException {

    private static final long serialVersionUID = 1L;

    private static final Set<String> SOLD_OUT_REASONS = Set.of("sold_out", "not_enough_together");

    SeatLayerConflictException(int status, String code, Map<String, Object> body, String requestId, String message) {
        super(status, code, body, requestId, message);
    }

    /** Per-object conflicts, when the endpoint reports them. */
    public List<Map<String, Object>> conflicts() {
        return conflictsOf(body());
    }

    /** Best-available could not find enough free inventory. */
    public boolean isSoldOut() {
        return body().get("reason") instanceof String reason && SOLD_OUT_REASONS.contains(reason);
    }
}
