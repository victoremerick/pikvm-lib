package org.pikvm.exception;

/** Thrown when authentication fails (HTTP 401/403). */
public class PiKvmAuthException extends PiKvmException {
    public PiKvmAuthException(String message)                  { super(message); }
    public PiKvmAuthException(String message, Throwable cause) { super(message, cause); }
}
