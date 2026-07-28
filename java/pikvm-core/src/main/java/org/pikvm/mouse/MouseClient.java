package org.pikvm.mouse;

import com.google.gson.Gson;
import org.pikvm.endpoint.BaseEndpoint;
import org.pikvm.http.PiKvmHttpClient;
import org.pikvm.websocket.PiKvmWebSocketClient;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Mouse input client.
 * <p>
 * Equivalent to {@code PiKVMMouse} in Python. Handles button events,
 * movement (with coordinate scaling to PiKVM 16-bit space), wheel events,
 * and click helpers.
 * </p>
 */
public class MouseClient extends BaseEndpoint {

    private static final Logger LOGGER = Logger.getLogger(MouseClient.class.getName());
    private static final Gson   GSON   = new Gson();

    private final PiKvmWebSocketClient wsClient;

    /** Cached screen dimensions used for coordinate scaling. */
    private Integer screenWidth;
    private Integer screenHeight;

    /** Set to true to enable verbose debug logging. */
    public boolean extraVerbose = false;

    public MouseClient(PiKvmHttpClient httpClient,
                       PiKvmWebSocketClient wsClient) {
        super(httpClient);
        this.wsClient = wsClient;
    }

    // ── Button events ──────────────────────────────────────────────────────

    /**
     * Sends a mouse button press or release event.
     * Equivalent to {@code send_mouse_event(button, state)} in Python.
     *
     * @param button mouse button
     * @param pressed true = press, false = release
     */
    public void sendMouseEvent(MouseButton button, boolean pressed) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("event_type", "mouse_button");
        Map<String, String> inner = new LinkedHashMap<>();
        inner.put("button", button.getApiValue());
        inner.put("state",  pressed ? "true" : "false");
        event.put("event", inner);
        wsClient.sendWithRetry(GSON.toJson(event));
        if (extraVerbose) {
            LOGGER.fine("Mouse button " + button.getApiValue() + " " + (pressed ? "pressed" : "released"));
        }
    }

    /**
     * Sends a mouse click (press + release with delay).
     * Equivalent to {@code send_click(button, delay)} in Python.
     */
    public void sendClick(MouseButton button, long delayMs) {
        sendMouseEvent(button, true);
        sleep(delayMs);
        sendMouseEvent(button, false);
        if (extraVerbose) LOGGER.fine("Mouse " + button.getApiValue() + " click completed");
    }

    /** Overload with default 50 ms delay. */
    public void sendClick(MouseButton button) {
        sendClick(button, 50);
    }

    // ── Movement ──────────────────────────────────────────────────────────

    /**
     * Scales screen pixel coordinates to PiKVM 16-bit signed integer space.
     * Equivalent to {@code _scale_mouse_xy_to_i16()} in Python.
     *
     * @param screenX  pixel X coordinate
     * @param screenY  pixel Y coordinate
     * @param width    screen width in pixels
     * @param height   screen height in pixels
     * @return array {@code [kvmX, kvmY]} in range [-32768, 32767]
     */
    public static int[] scaleToI16(int screenX, int screenY, int width, int height) {
        int x = (int) ((double) screenX / width  * 0xFFFF - 0x8000);
        int y = (int) ((double) screenY / height * 0xFFFF - 0x8000);
        return new int[]{x, y};
    }

    /**
     * Sends a mouse move event.
     * Equivalent to {@code send_mouse_move_event(x, y)} in Python.
     * <p>
     * If screen dimensions are not set, they are fetched automatically
     * via the streamer snapshot.
     * </p>
     *
     * @param x target X in screen pixels
     * @param y target Y in screen pixels
     */
    public void sendMouseMoveEvent(int x, int y) {
        // Screen size is set externally (e.g. from PiKvmClient after streamer snapshot)
        if (screenWidth == null || screenHeight == null) {
            throw new IllegalStateException(
                "Screen dimensions not set. Call setScreenDimensions() or use PiKvmClient.");
        }
        int[] kvm = scaleToI16(x, y, screenWidth, screenHeight);
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("event_type", "mouse_move");
        Map<String, Object> toMap = new LinkedHashMap<>();
        toMap.put("x", kvm[0]);
        toMap.put("y", kvm[1]);
        Map<String, Object> inner = new LinkedHashMap<>();
        inner.put("to", toMap);
        event.put("event", inner);
        wsClient.sendWithRetry(GSON.toJson(event));
        if (extraVerbose) {
            LOGGER.fine("Mouse moved to x:" + x + " y:" + y
                        + " (KVM coords: " + kvm[0] + ", " + kvm[1] + ")");
        }
    }

    // ── Wheel ──────────────────────────────────────────────────────────────

    /**
     * Sends a mouse wheel scroll event.
     * Equivalent to {@code send_mouse_wheel_event(delta)} in Python.
     *
     * @param delta scroll delta (-1 = down, 1 = up)
     */
    public void sendMouseWheelEvent(int delta) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("event_type", "mouse_wheel");
        Map<String, Integer> inner = new LinkedHashMap<>();
        inner.put("delta", delta);
        event.put("event", inner);
        wsClient.sendWithRetry(GSON.toJson(event));
        if (extraVerbose) LOGGER.fine("Mouse wheel delta: " + delta);
    }

    // ── Screen dimensions ─────────────────────────────────────────────────

    public void setScreenDimensions(int width, int height) {
        this.screenWidth  = width;
        this.screenHeight = height;
    }

    public Integer getScreenWidth()  { return screenWidth; }
    public Integer getScreenHeight() { return screenHeight; }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
