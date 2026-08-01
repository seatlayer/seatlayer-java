package io.seatlayer;

import java.util.Map;

/** 401/403 — bad key, revoked key, or a live key used against a test event. */
public class SeatLayerAuthException extends SeatLayerException {

    private static final long serialVersionUID = 1L;

    SeatLayerAuthException(int status, String code, Map<String, Object> body, String requestId, String message) {
        super(status, code, body, requestId, message);
    }

    /**
     * The key's mode and the event's mode disagree — the most common cause of a
     * "works locally, 403s in production" report.
     */
    public boolean isModeMismatch() {
        return "mode_mismatch".equals(code());
    }
}
