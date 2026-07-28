package org.pikvm.test;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.pikvm.http.PiKvmHttpClient;
import org.pikvm.keyboard.KeyboardClient;
import org.pikvm.keymap.KeyboardAliasResolver;
import org.pikvm.websocket.PiKvmWebSocketClient;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link KeyboardClient} and {@link KeyboardAliasResolver}.
 * Equivalent to {@code test_pikvm_keyboard.py}.
 */
@ExtendWith(MockitoExtension.class)
class KeyboardClientTest {

    @Mock PiKvmHttpClient     httpClient;
    @Mock PiKvmWebSocketClient wsClient;

    private KeyboardClient keyboard;

    @BeforeEach
    void setUp() {
        keyboard = new KeyboardClient(httpClient, wsClient);
    }

    // ── KeyboardAliasResolver tests ───────────────────────────────────────

    @Test
    void testKeyToKeycodeStandard() {
        assertEquals("KeyA", KeyboardAliasResolver.toKeycode("a"));
        assertEquals("KeyZ", KeyboardAliasResolver.toKeycode("z"));
        assertEquals("Digit5", KeyboardAliasResolver.toKeycode("5"));
        assertEquals("Space", KeyboardAliasResolver.toKeycode(" "));
    }

    @Test
    void testKeyToKeycodeSpecial() {
        assertEquals("Enter",       KeyboardAliasResolver.toKeycode("enter"));
        assertEquals("Tab",         KeyboardAliasResolver.toKeycode("tab"));
        assertEquals("Comma",       KeyboardAliasResolver.toKeycode(","));
        assertEquals("Minus",       KeyboardAliasResolver.toKeycode("_")); // shift
        assertEquals("ControlLeft", KeyboardAliasResolver.toKeycode("ctrl"));
    }

    @Test
    void testRequiresShift() {
        assertTrue(KeyboardAliasResolver.requiresShift("A"));   // uppercase
        assertTrue(KeyboardAliasResolver.requiresShift("\""));  // quote
        assertTrue(KeyboardAliasResolver.requiresShift("_"));   // shift map
        assertFalse(KeyboardAliasResolver.requiresShift("a"));  // lowercase
        assertFalse(KeyboardAliasResolver.requiresShift("1"));  // digit
    }

    // ── KeyboardClient send tests ─────────────────────────────────────────

    @Test
    void testKeyDown() {
        when(wsClient.createEvent(anyString(), anyBoolean())).thenReturn("mock-event");
        keyboard.keyDown("a");
        verify(wsClient).sendWithRetry(wsClient.createEvent("KeyA", true));
    }

    @Test
    void testKeyUp() {
        when(wsClient.createEvent(anyString(), anyBoolean())).thenReturn("mock-event");
        keyboard.keyUp("a");
        verify(wsClient).sendWithRetry(wsClient.createEvent("KeyA", false));
    }

    @Test
    void testUpperCaseKeyDownSendsShift() {
        when(wsClient.createEvent(anyString(), anyBoolean())).thenReturn("mock-event");
        keyboard.keyDown("A");
        verify(wsClient).createEvent("ShiftLeft", true);
        verify(wsClient).createEvent("KeyA", true);
    }

    @Test
    void testHotkey() {
        when(wsClient.createEvent(anyString(), anyBoolean())).thenReturn("mock-event");
        keyboard.hotkey("ctrl", "alt", "delete");
        // ctrl/alt/delete pressed in order, then released in reverse
        verify(wsClient).createEvent("ControlLeft", true);
        verify(wsClient).createEvent("AltLeft",     true);
        verify(wsClient).createEvent("Delete",       true);
        verify(wsClient).createEvent("Delete",       false);
        verify(wsClient).createEvent("AltLeft",     false);
        verify(wsClient).createEvent("ControlLeft", false);
    }
}
