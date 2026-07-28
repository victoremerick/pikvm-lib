package org.pikvm.test;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.pikvm.http.PiKvmHttpClient;
import org.pikvm.keyboard.KeyboardClient;
import org.pikvm.keymap.Keymaps;
import org.pikvm.websocket.PiKvmWebSocketClient;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for WebSocket event creation and keyboard input sequences.
 * Equivalent to {@code test_pikvm_websocket.py}.
 */
@ExtendWith(MockitoExtension.class)
class WebSocketClientTest {

    @Mock PiKvmWebSocketClient wsClient;
    @Mock PiKvmHttpClient      httpClient;

    @Test
    void testCreateEventKeyTrue() {
        PiKvmWebSocketClient real = mock(PiKvmWebSocketClient.class, CALLS_REAL_METHODS);
        // Test event format for pressed key
        String event = real.createEvent("KeyA", true);
        assertTrue(event.contains("\"event_type\": \"key\""), event);
        assertTrue(event.contains("\"key\": \"KeyA\""), event);
        assertTrue(event.contains("\"state\": true"), event);
    }

    @Test
    void testCreateEventKeyFalse() {
        PiKvmWebSocketClient real = mock(PiKvmWebSocketClient.class, CALLS_REAL_METHODS);
        String event = real.createEvent("ShiftLeft", false);
        assertTrue(event.contains("\"state\": false"), event);
    }

    @Test
    void testKeymapBaseContainsExpectedKeys() {
        assertTrue(Keymaps.BASE.containsKey("\n"));
        assertTrue(Keymaps.BASE.containsKey("<Enter>"));
        assertTrue(Keymaps.BASE.containsKey("<F1>"));
        assertTrue(Keymaps.BASE.containsKey("<ArrowUp>"));
        assertEquals("Enter",    Keymaps.BASE.get("\n"));
        assertEquals("F1",       Keymaps.BASE.get("<F1>"));
        assertEquals("ArrowUp",  Keymaps.BASE.get("<ArrowUp>"));
    }

    @Test
    void testKeymapShiftContainsSpecialChars() {
        assertEquals("Minus",    Keymaps.SHIFT.get("_"));
        assertEquals("Digit2",   Keymaps.SHIFT.get("@"));
        assertEquals("Digit8",   Keymaps.SHIFT.get("*"));
    }

    @Test
    void testKeymapPyautoguiContainsCtrl() {
        assertEquals("ControlLeft",  Keymaps.PYAUTOGUI.get("ctrl"));
        assertEquals("AltLeft",      Keymaps.PYAUTOGUI.get("alt"));
        assertEquals("Delete",       Keymaps.PYAUTOGUI.get("delete"));
        assertEquals("ShiftLeft",    Keymaps.PYAUTOGUI.get("shift"));
    }

    @Test
    void testHotkeyKeyboardSequence() {
        when(wsClient.createEvent(anyString(), anyBoolean())).thenReturn("event");
        KeyboardClient kb = new KeyboardClient(httpClient, wsClient);
        kb.hotkey("ctrl", "alt", "delete");

        // Pressed in order: ctrl, alt, delete
        verify(wsClient).createEvent("ControlLeft", true);
        verify(wsClient).createEvent("AltLeft",     true);
        verify(wsClient).createEvent("Delete",       true);
        // Released in reverse: delete, alt, ctrl
        verify(wsClient).createEvent("Delete",       false);
        verify(wsClient).createEvent("AltLeft",     false);
        verify(wsClient).createEvent("ControlLeft", false);
    }
}
