package org.pikvm.websocket;

/**
 * Retry configuration for WebSocket operations.
 * Equivalent to {@code max_retries} and {@code retry_delay} parameters in Python.
 */
public final class WebSocketRetryPolicy {

    public static final WebSocketRetryPolicy DEFAULT = new WebSocketRetryPolicy(3, 1_000L);

    private final int  maxRetries;
    /** Delay between retry attempts in milliseconds. */
    private final long retryDelayMs;

    public WebSocketRetryPolicy(int maxRetries, long retryDelayMs) {
        this.maxRetries   = maxRetries;
        this.retryDelayMs = retryDelayMs;
    }

    public int  getMaxRetries()   { return maxRetries; }
    public long getRetryDelayMs() { return retryDelayMs; }
}
