package io.seatlayer;

import java.util.Map;

/** 429. {@link #retryAfterSeconds()} prefers the header over the JSON field. */
public class SeatLayerRateLimitException extends SeatLayerException {

    private static final long serialVersionUID = 1L;

    private final double retryAfterSeconds;

    SeatLayerRateLimitException(
            int status,
            String code,
            Map<String, Object> body,
            String requestId,
            String message,
            double retryAfterSeconds) {
        super(status, code, body, requestId, message);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public double retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
