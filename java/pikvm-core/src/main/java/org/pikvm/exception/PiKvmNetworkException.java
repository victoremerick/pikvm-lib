package org.pikvm.exception;

/** Thrown on transport-level errors (connection refused, timeout, TLS, etc.). */
public class PiKvmNetworkException extends PiKvmException {
    public PiKvmNetworkException(String message)                  { super(message); }
    public PiKvmNetworkException(String message, Throwable cause) { super(message, cause); }
}
