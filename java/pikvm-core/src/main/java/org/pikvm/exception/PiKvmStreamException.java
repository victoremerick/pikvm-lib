package org.pikvm.exception;

/** Thrown when the streamer fails to initialise or returns invalid data. */
public class PiKvmStreamException extends PiKvmException {
    public PiKvmStreamException(String message)                  { super(message); }
    public PiKvmStreamException(String message, Throwable cause) { super(message, cause); }
}
