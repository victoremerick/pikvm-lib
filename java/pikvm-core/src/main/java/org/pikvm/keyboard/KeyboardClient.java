package org.pikvm.keyboard;

import org.pikvm.endpoint.BaseEndpoint;
import org.pikvm.http.PiKvmHttpClient;
import org.pikvm.keymap.KeyboardAliasResolver;
import org.pikvm.websocket.PiKvmWebSocketClient;

/**
 * High-level keyboard client aligned with the pyautogui API style.
 * <p>
 * Equivalent to {@code PiKVMKeyboard} in Python.
 * </p>
 *
 * <p>Usage example:
 * <pre>{@code
 * pikvm.getKeyboard().press("a");
 * pikvm.getKeyboard().hotkey("ctrl", "alt", "delete");
 * }</pre>
 * </p>
 */
public class KeyboardClient extends BaseEndpoint {

    private final PiKvmWebSocketClient wsClient;

    public KeyboardClient(PiKvmHttpClient httpClient, PiKvmWebSocketClient wsClient) {
        super(httpClient);
        this.wsClient = wsClient;
    }

    // ── Core API (mirrors pyautogui) ──────────────────────────────────────

    /**
     * Sends a key-down event.
     * Equivalent to {@code keyDown(key)} in Python.
     */
    public void keyDown(String key) {
        if (KeyboardAliasResolver.requiresShift(key)) {
            wsClient.sendWithRetry(wsClient.createEvent("ShiftLeft", true));
        }
        wsClient.sendWithRetry(wsClient.createEvent(KeyboardAliasResolver.toKeycode(key), true));
    }

    /**
     * Sends a key-up event.
     * Equivalent to {@code keyUp(key)} in Python.
     */
    public void keyUp(String key) {
        if (KeyboardAliasResolver.requiresShift(key)) {
            wsClient.sendWithRetry(wsClient.createEvent("ShiftLeft", false));
        }
        wsClient.sendWithRetry(wsClient.createEvent(KeyboardAliasResolver.toKeycode(key), false));
    }

    /**
     * Presses and releases a key with default 50 ms hold.
     * Equivalent to {@code press(key)} in Python.
     */
    public void press(String key) {
        press(key, 50);
    }

    /**
     * Presses and releases a key with a configurable hold delay.
     *
     * @param key      key string
     * @param delayMs  hold duration in milliseconds
     */
    public void press(String key, long delayMs) {
        keyDown(key);
        sleep(delayMs);
        keyUp(key);
    }

    /**
     * Sends a hotkey combination (presses all keys, then releases in reverse).
     * Equivalent to {@code hotkey(*keys)} in Python.
     * <p>Example: {@code hotkey("ctrl", "alt", "delete")}</p>
     */
    public void hotkey(String... keys) {
        for (String key : keys) {
            keyDown(key);
            sleep(50);
        }
        for (int i = keys.length - 1; i >= 0; i--) {
            keyUp(keys[i]);
            sleep(50);
        }
    }

    /**
     * Sends a full text string via {@link PiKvmWebSocketClient#sendInput(String)}.
     *
     * @param text text to type
     */
    public void typeText(String text) {
        wsClient.sendInput(text);
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────

    public PiKvmWebSocketClient getWsClient() { return wsClient; }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
