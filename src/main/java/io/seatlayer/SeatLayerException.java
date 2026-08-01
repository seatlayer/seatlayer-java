package io.seatlayer;

import java.util.List;
import java.util.Map;

/**
 * Base class for every API error.
 *
 * <p>The API answers failures with {@code {"error": ..., "code": ..., "message": ...}}
 * and a status. Surfacing that as one opaque exception leaves every caller
 * string-matching on {@code error}. The subclasses are the ones an integration actually
 * branches on — a sold-out seat is a business outcome that belongs in an {@code if}, not
 * in a {@code catch} that also swallows a bad key.
 */
public class SeatLayerException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final int status;
    private final String code;
    private final transient Map<String, Object> body;
    private final String requestId;

    SeatLayerException(int status, String code, Map<String, Object> body, String requestId, String message) {
        super(message);
        this.status = status;
        this.code = code;
        this.body = body;
        this.requestId = requestId;
    }

    /** HTTP status the API answered with. */
    public int status() {
        return status;
    }

    /** Machine-readable code: {@code body.code} falling back to {@code body.error}. */
    public String code() {
        return code;
    }

    /** The decoded error body, for fields this SDK does not model. */
    public Map<String, Object> body() {
        return body;
    }

    /** Correlation id from {@code X-Request-ID}. Quote it in support requests. */
    public String requestId() {
        return requestId;
    }

    static SeatLayerException fromResponse(
            int status, Map<String, Object> body, String requestId, double retryAfterSeconds) {
        String code = firstString(body.get("code"), body.get("error"), "unknown_error");
        Object rawMessage = body.get("message");
        String message = rawMessage instanceof String s && !s.isBlank()
                ? s
                : "SeatLayer API error " + status + " (" + code + ")";

        return switch (status) {
            case 401, 403 -> new SeatLayerAuthException(status, code, body, requestId, message);
            case 404 -> new SeatLayerNotFoundException(status, code, body, requestId, message);
            case 409 -> new SeatLayerConflictException(status, code, body, requestId, message);
            case 422 -> new SeatLayerValidationException(status, code, body, requestId, message);
            case 429 -> new SeatLayerRateLimitException(
                    status, code, body, requestId, message, retryAfterSeconds);
            default -> new SeatLayerException(status, code, body, requestId, message);
        };
    }

    private static String firstString(Object first, Object second, String fallback) {
        if (first instanceof String s && !s.isBlank()) {
            return s;
        }
        if (second instanceof String s && !s.isBlank()) {
            return s;
        }
        return fallback;
    }

    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> conflictsOf(Map<String, Object> body) {
        Object conflicts = body.get("conflicts");
        return conflicts instanceof List<?> list
                ? (List<Map<String, Object>>) (List<?>) list
                : List.of();
    }
}
