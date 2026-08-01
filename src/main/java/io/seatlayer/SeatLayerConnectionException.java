package io.seatlayer;

/** The request never got an answer: DNS, TLS, socket, or timeout. */
public class SeatLayerConnectionException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    SeatLayerConnectionException(String message, Throwable cause) {
        super(message, cause);
    }
}
