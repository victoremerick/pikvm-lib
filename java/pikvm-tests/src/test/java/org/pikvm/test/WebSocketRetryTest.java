package org.pikvm.test;

import org.junit.jupiter.api.Test;
import org.pikvm.websocket.WebSocketRetryPolicy;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link WebSocketRetryPolicy}.
 * Equivalent to {@code test_pikvm_websocket_retry.py}.
 */
class WebSocketRetryTest {

    @Test
    void testDefaultPolicy() {
        WebSocketRetryPolicy p = WebSocketRetryPolicy.DEFAULT;
        assertEquals(3,     p.getMaxRetries());
        assertEquals(1000L, p.getRetryDelayMs());
    }

    @Test
    void testCustomPolicy() {
        WebSocketRetryPolicy p = new WebSocketRetryPolicy(5, 2000L);
        assertEquals(5,     p.getMaxRetries());
        assertEquals(2000L, p.getRetryDelayMs());
    }
}
