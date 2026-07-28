package org.pikvm.exception;

/** Thrown when the PiKVM API returns an error response. */
public class PiKvmApiException extends PiKvmException {
    private final int statusCode;

    public PiKvmApiException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    /** HTTP status code returned by the server. */
    public int getStatusCode() { return statusCode; }
}
