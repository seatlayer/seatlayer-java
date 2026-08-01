package io.seatlayer;

import java.util.Map;

/** 422 — the request was understood and rejected. */
public class SeatLayerValidationException extends SeatLayerException {

    private static final long serialVersionUID = 1L;

    SeatLayerValidationException(int status, String code, Map<String, Object> body, String requestId, String message) {
        super(status, code, body, requestId, message);
    }
}
