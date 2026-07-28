package org.pikvm;

import org.pikvm.atx.AtxClient;
import org.pikvm.atx.AtxButtonAction;
import org.pikvm.atx.AtxPowerAction;
import org.pikvm.config.PiKvmConfig;
import org.pikvm.exception.PiKvmAuthException;
import org.pikvm.exception.PiKvmNetworkException;
import org.pikvm.gpio.GpioClient;
import org.pikvm.http.PiKvmHttpClient;
import org.pikvm.keyboard.KeyboardClient;
import org.pikvm.mouse.MouseButton;
import org.pikvm.mouse.MouseClient;
import org.pikvm.msd.MsdClient;
import org.pikvm.msd.MsdParameters;
import org.pikvm.streamer.SnapshotResult;
import org.pikvm.streamer.StreamerClient;
import org.pikvm.websocket.PiKvmWebSocketClient;
import org.pikvm.websocket.WebSocketRetryPolicy;
import okhttp3.Response;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Main facade for controlling a PiKVM device.
 * <p>
 * Equivalent to {@code PiKVM} in Python. Aggregates all subsystem clients
 * (ATX, GPIO, MSD, Streamer, Mouse, Keyboard) behind a single entry point.
 * </p>
 *
 * <p>Quick start:
 * <pre>{@code
 * PiKvmClient pikvm = PiKvmClient.builder("192.168.1.10", "admin", "password").build();
 * System.out.println(pikvm.getSystemInfo());
 * pikvm.setAtxPower(AtxPowerAction.ON);
 * pikvm.getKeyboard().press("a");
 * pikvm.close();
 * }</pre>
 * </p>
 */
public class PiKvmClient implements AutoCloseable {

    private static final Logger LOGGER = Logger.getLogger(PiKvmClient.class.getName());

    private final PiKvmConfig         config;
    private final PiKvmHttpClient     httpClient;
    private PiKvmWebSocketClient      wsClient;

    private final AtxClient      atx;
    private final GpioClient     gpio;
    private final MsdClient      msd;
    private final StreamerClient streamer;
    private final MouseClient    mouse;
    private final KeyboardClient keyboard;

    // ── Constructor / Builder ─────────────────────────────────────────────

    private PiKvmClient(Builder b) {
        this.config     = b.config;
        this.httpClient = new PiKvmHttpClient(config);

        if (b.initWsClient) {
            this.wsClient = new PiKvmWebSocketClient(config,
                new WebSocketRetryPolicy(b.maxWsRetries, b.wsRetryDelayMs),
                false, false);
        } else {
            this.wsClient = b.wsClient;
        }

        this.atx      = new AtxClient(httpClient);
        this.gpio     = new GpioClient(httpClient);
        this.msd      = new MsdClient(httpClient);
        this.streamer = new StreamerClient(httpClient, wsClient);
        this.mouse    = new MouseClient(httpClient, wsClient);
        this.keyboard = new KeyboardClient(httpClient, wsClient);
    }

    public static Builder builder(String hostname, String username, String password) {
        return new Builder(hostname, username, password);
    }

    // ── Authentication ────────────────────────────────────────────────────

    /**
     * Checks if credentials are accepted by the PiKVM device.
     * Equivalent to {@code _auth()} in Python.
     */
    public boolean isAuthenticated() {
        try (Response r = httpClient.get("/api/auth/check")) {
            return r.code() == 200;
        } catch (IOException e) {
            throw new PiKvmNetworkException("Auth check failed", e);
        }
    }

    /**
     * Logs and returns auth status.
     * Equivalent to {@code isauth()} in Python.
     */
    public boolean isAuth() {
        boolean auth = isAuthenticated();
        LOGGER.info(auth ? "User is authenticated" : "User NOT authenticated");
        return auth;
    }

    // ── System info ───────────────────────────────────────────────────────

    /**
     * Returns system information.
     * Equivalent to {@code get_system_info()}.
     */
    public Map<String, Object> getSystemInfo() {
        return httpClient.getInfos("/api/info");
    }

    /**
     * Logs the system log (last {@code seekSeconds} seconds).
     * Equivalent to {@code get_system_log()}.
     */
    public void logSystemLog(int seekSeconds) {
        try (Response r = httpClient.get("/api/log", "seek=" + seekSeconds)) {
            if (r.body() != null) LOGGER.fine(r.body().string());
        } catch (IOException e) {
            throw new PiKvmNetworkException("Failed to fetch system log", e);
        }
    }

    /** Overload with default 3600 s. */
    public void logSystemLog() { logSystemLog(3600); }

    /**
     * Returns Prometheus metrics.
     * Equivalent to {@code get_prometheus_metrics()}.
     */
    public Map<String, Object> getPrometheusMetrics() {
        return httpClient.getInfos("/api/export/prometheus/metrics");
    }

    // ── ATX delegates ─────────────────────────────────────────────────────

    public Map<String, Object> getAtxState()             { return atx.getAtxState(); }
    public void setAtxPower(AtxPowerAction action)        { atx.setAtxPower(action); }
    public void clickAtxButton(AtxButtonAction button)    { atx.clickAtxButton(button); }

    // ── GPIO delegates ────────────────────────────────────────────────────

    public Map<String, Object> getGpioState()                              { return gpio.getGpioState(); }
    public void switchGpioChannel(String channel, int state, Integer wait) { gpio.switchGpioChannel(channel, state, wait); }
    public void switchGpioChannel(String channel)                          { gpio.switchGpioChannel(channel); }
    public void pulseGpioChannel(String channel, Double delay, Integer wait){ gpio.pulseGpioChannel(channel, delay, wait); }
    public void pulseGpioChannel(String channel)                           { gpio.pulseGpioChannel(channel); }

    // ── MSD delegates ─────────────────────────────────────────────────────

    public Map<String, Object> getMsdState()                             { return msd.getMsdState(); }
    public void uploadMsdImage(File file)                                { msd.uploadMsdImage(file); }
    public void uploadMsdImage(File file, String imageName)              { msd.uploadMsdImage(file, imageName); }
    public void uploadMsdRemote(String url)                              { msd.uploadMsdRemote(url); }
    public void uploadMsdRemote(String url, String imageName)            { msd.uploadMsdRemote(url, imageName); }
    public void setMsdParameters(MsdParameters p)                        { msd.setMsdParameters(p); }
    public void connectMsd()                                             { msd.connectMsd(); }
    public void disconnectMsd()                                          { msd.disconnectMsd(); }
    public void removeMsdImage(String imageName)                         { msd.removeMsdImage(imageName); }
    public void resetMsd()                                               { msd.resetMsd(); }

    // ── Streamer delegates ────────────────────────────────────────────────

    public Map<String, Object> getStreamerState()                    { return streamer.getStreamerState(); }

    /**
     * Captures a snapshot and returns raw bytes.
     * Equivalent to {@code get_streamer_image()}.
     */
    public SnapshotResult getStreamerImage() {
        ensureStreamer();
        return streamer.captureSnapshot(false, 3);
    }

    /**
     * Captures a snapshot to a file.
     * Equivalent to {@code get_streamer_snapshot(snapshot_path, filename, ocr)}.
     */
    public File getStreamerSnapshot(File outputFile, boolean ocr) {
        ensureStreamer();
        return streamer.captureSnapshotToFile(outputFile, ocr);
    }

    public File getStreamerSnapshot(File outputFile) {
        return getStreamerSnapshot(outputFile, false);
    }

    // ── Mouse delegates ───────────────────────────────────────────────────

    /**
     * Sends a mouse move event.
     * Equivalent to {@code send_mouse_move_event(x, y)}.
     * <p>Auto-detects screen size from streamer if not yet known.</p>
     */
    public void sendMouseMoveEvent(int x, int y) {
        if (mouse.getScreenWidth() == null) {
            SnapshotResult snap = getStreamerImage();
            // Decode dimensions from JPEG header: width & height from bytes 0xFFD8...
            int[] dims = jpegDimensions(snap.getImageBytes());
            mouse.setScreenDimensions(dims[0], dims[1]);
        }
        mouse.sendMouseMoveEvent(x, y);
    }

    public void sendMouseEvent(MouseButton button, boolean pressed) { mouse.sendMouseEvent(button, pressed); }
    public void sendClick(MouseButton button)                        { mouse.sendClick(button); }
    public void sendClick(MouseButton button, long delayMs)          { mouse.sendClick(button, delayMs); }
    public void sendMouseWheelEvent(int delta)                       { mouse.sendMouseWheelEvent(delta); }

    // ── Keyboard delegates ────────────────────────────────────────────────

    public void keyDown(String key)      { keyboard.keyDown(key); }
    public void keyUp(String key)        { keyboard.keyUp(key); }
    public void press(String key)        { keyboard.press(key); }
    public void hotkey(String... keys)   { keyboard.hotkey(keys); }
    public void typeText(String text)    { keyboard.typeText(text); }

    // ── WebSocket ─────────────────────────────────────────────────────────

    public void attachWsClient(PiKvmWebSocketClient wsClient) {
        this.wsClient = wsClient;
        streamer.setWsClient(wsClient);
    }

    // ── Accessors ─────────────────────────────────────────────────────────

    public PiKvmConfig         getConfig()   { return config; }
    public AtxClient           getAtx()      { return atx; }
    public GpioClient          getGpio()     { return gpio; }
    public MsdClient           getMsd()      { return msd; }
    public StreamerClient      getStreamer() { return streamer; }
    public MouseClient         getMouse()    { return mouse; }
    public KeyboardClient      getKeyboard() { return keyboard; }
    public PiKvmWebSocketClient getWsClient(){ return wsClient; }
    public PiKvmHttpClient     getHttpClient(){ return httpClient; }

    @Override
    public void close() {
        if (wsClient != null) wsClient.close();
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private void ensureStreamer() {
        streamer.ensureStreamerStarted(() ->
            new PiKvmWebSocketClient(config,
                new WebSocketRetryPolicy(3, 1000L), true, false)
        );
    }

    /** Minimal JPEG SOF0 parser to extract image width and height. */
    private static int[] jpegDimensions(byte[] jpeg) {
        for (int i = 0; i < jpeg.length - 9; i++) {
            // Look for SOF0 (0xFFC0), SOF1, SOF2 markers
            if ((jpeg[i] & 0xFF) == 0xFF) {
                int marker = jpeg[i + 1] & 0xFF;
                if (marker == 0xC0 || marker == 0xC1 || marker == 0xC2) {
                    int height = ((jpeg[i + 5] & 0xFF) << 8) | (jpeg[i + 6] & 0xFF);
                    int width  = ((jpeg[i + 7] & 0xFF) << 8) | (jpeg[i + 8] & 0xFF);
                    if (width > 0 && height > 0) return new int[]{width, height};
                }
            }
        }
        return new int[]{1920, 1080}; // fallback
    }

    // ── Builder ───────────────────────────────────────────────────────────

    public static final class Builder {
        private final PiKvmConfig.Builder configBuilder;
        private boolean              initWsClient   = true;
        private PiKvmWebSocketClient wsClient       = null;
        private int                  maxWsRetries   = 3;
        private long                 wsRetryDelayMs = 1000L;

        private PiKvmConfig config;

        private Builder(String hostname, String username, String password) {
            this.configBuilder = PiKvmConfig.builder(hostname, username, password);
        }

        public Builder secret(String secret)                      { configBuilder.secret(secret); return this; }
        public Builder schema(String schema)                      { configBuilder.schema(schema); return this; }
        public Builder certificateTrusted(boolean t)              { configBuilder.certificateTrusted(t); return this; }
        public Builder maxWsRetries(int n)                        { this.maxWsRetries = n; return this; }
        public Builder wsRetryDelayMs(long ms)                    { this.wsRetryDelayMs = ms; return this; }
        /** Provide a pre-built WebSocket client (disables auto-init). */
        public Builder wsClient(PiKvmWebSocketClient ws)          { this.wsClient = ws; this.initWsClient = false; return this; }
        /** Disable automatic WebSocket initialisation. */
        public Builder noWsClient()                               { this.initWsClient = false; return this; }

        public PiKvmClient build() {
            this.config = configBuilder.build();
            return new PiKvmClient(this);
        }
    }
}
