package io.seatlayer;

import java.util.Map;

/**
 * 404 — including another organisation's resource.
 *
 * <p>Asking for something owned by a different organisation answers 404, never 403: a
 * 403 would confirm the resource exists, which is not something one customer should be
 * able to learn about another.
 */
public class SeatLayerNotFoundException extends SeatLayerException {

    private static final long serialVersionUID = 1L;

    SeatLayerNotFoundException(int status, String code, Map<String, Object> body, String requestId, String message) {
        super(status, code, body, requestId, message);
    }
}
