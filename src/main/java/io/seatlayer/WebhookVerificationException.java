package io.seatlayer;

/** The delivery did not come from SeatLayer. Respond 400; do not process it. */
public class WebhookVerificationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    WebhookVerificationException(String message) {
        super(message);
    }
}
