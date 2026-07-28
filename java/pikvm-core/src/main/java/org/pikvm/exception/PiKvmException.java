package org.pikvm.exception;

/** Base exception for all PiKVM library errors. */
public class PiKvmException extends RuntimeException {
    public PiKvmException(String message)                  { super(message); }
    public PiKvmException(String message, Throwable cause) { super(message, cause); }
}
