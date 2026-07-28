package org.pikvm.test;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.pikvm.http.PiKvmHttpClient;
import org.pikvm.mouse.MouseButton;
import org.pikvm.mouse.MouseClient;
import org.pikvm.websocket.PiKvmWebSocketClient;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link MouseClient}. Equivalent to {@code test_pikvm_mouse.py}.
 */
@ExtendWith(MockitoExtension.class)
class MouseClientTest {

    @Mock PiKvmHttpClient     httpClient;
    @Mock PiKvmWebSocketClient wsClient;

    private MouseClient mouse;
    private final Gson  gson = new Gson();

    @BeforeEach
    void setUp() {
        mouse = new MouseClient(httpClient, wsClient);
        mouse.setScreenDimensions(1920, 1080);
    }

    // ── scaleToI16 tests ──────────────────────────────────────────────────

    @Test
    void testScaleToI16Center() {
        int[] kvm = MouseClient.scaleToI16(960, 540, 1920, 1080);
        assertEquals(0, kvm[0]);
        assertEquals(0, kvm[1]);
    }

    @Test
    void testScaleToI16TopLeft() {
        int[] kvm = MouseClient.scaleToI16(0, 0, 1920, 1080);
        assertEquals(-32768, kvm[0]);
        assertEquals(-32768, kvm[1]);
    }

    @Test
    void testScaleToI16BottomRight() {
        int[] kvm = MouseClient.scaleToI16(1920, 1080, 1920, 1080);
        assertEquals(32767, kvm[0]);
        assertEquals(32767, kvm[1]);
    }

    // ── Button event tests ────────────────────────────────────────────────

    @Test
    void testSendMouseEventLeft() {
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        mouse.sendMouseEvent(MouseButton.LEFT, true);
        verify(wsClient).sendWithRetry(captor.capture());
        Map<String, Object> event = gson.fromJson(captor.getValue(),
            new TypeToken<Map<String, Object>>(){}.getType());
        assertEquals("mouse_button", event.get("event_type"));
        @SuppressWarnings("unchecked")
        Map<String, String> inner = (Map<String, String>) event.get("event");
        assertEquals("left", inner.get("button"));
        assertEquals("true", inner.get("state"));
    }

    @Test
    void testSendMouseEventRightRelease() {
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        mouse.sendMouseEvent(MouseButton.RIGHT, false);
        verify(wsClient).sendWithRetry(captor.capture());
        @SuppressWarnings("unchecked")
        Map<String, Object> inner =
            (Map<String, Object>) ((Map<String, Object>) gson.fromJson(captor.getValue(),
                new TypeToken<Map<String, Object>>(){}.getType())).get("event");
        assertEquals("false", inner.get("state"));
    }

    // ── Move event test ───────────────────────────────────────────────────

    @Test
    void testSendMouseMoveEvent() {
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        mouse.sendMouseMoveEvent(960, 540);
        verify(wsClient).sendWithRetry(captor.capture());
        Map<String, Object> event = gson.fromJson(captor.getValue(),
            new TypeToken<Map<String, Object>>(){}.getType());
        assertEquals("mouse_move", event.get("event_type"));
        @SuppressWarnings("unchecked")
        Map<String, Object> toMap =
            (Map<String, Object>) ((Map<String, Object>) event.get("event")).get("to");
        // 960/1920 * 0xFFFF - 0x8000 ≈ 0
        assertEquals(0.0, ((Number) toMap.get("x")).doubleValue(), 1.0);
        assertEquals(0.0, ((Number) toMap.get("y")).doubleValue(), 1.0);
    }

    // ── Wheel event test ──────────────────────────────────────────────────

    @Test
    void testSendMouseWheelEventUp() {
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        mouse.sendMouseWheelEvent(1);
        verify(wsClient).sendWithRetry(captor.capture());
        Map<String, Object> event = gson.fromJson(captor.getValue(),
            new TypeToken<Map<String, Object>>(){}.getType());
        assertEquals("mouse_wheel", event.get("event_type"));
        @SuppressWarnings("unchecked")
        Map<String, Object> inner = (Map<String, Object>) event.get("event");
        assertEquals(1.0, ((Number) inner.get("delta")).doubleValue(), 0.01);
    }

    // ── Click test ────────────────────────────────────────────────────────

    @Test
    void testSendClickLeft() {
        mouse.sendClick(MouseButton.LEFT);
        // press + release = 2 sendWithRetry calls
        verify(wsClient, times(2)).sendWithRetry(anyString());
    }
}
