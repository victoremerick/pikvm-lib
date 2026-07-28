package org.pikvm.websocket;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import okhttp3.*;
import okio.ByteString;
import org.pikvm.auth.AuthHeaderProvider;
import org.pikvm.config.PiKvmConfig;
import org.pikvm.exception.PiKvmNetworkException;
import org.pikvm.http.TlsHelper;
import org.pikvm.keymap.Keymaps;

import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * WebSocket client for real-time keyboard and mouse input to PiKVM.
 * <p>
 * Equivalent to {@code PiKVMWebsocket} in Python. Wraps OkHttp's async
 * WebSocket in a synchronous facade using a {@link LinkedBlockingQueue}
 * for received messages and {@link CountDownLatch} for connection setup.
 * </p>
 *
 * <p>Supports the retry + reconnect semantics of the Python implementation.</p>
 */
public class PiKvmWebSocketClient implements AutoCloseable {

    private static final Logger LOGGER = Logger.getLogger(PiKvmWebSocketClient.class.getName());

    private final PiKvmConfig         config;
    private final AuthHeaderProvider  auth;
    private final WebSocketRetryPolicy retryPolicy;
    private final boolean             activateStreamer;
    private final boolean             extraVerbose;
    private final OkHttpClient        okHttp;
    private final Gson                gson = new Gson();

    private volatile okhttp3.WebSocket ws;
    private final    AtomicBoolean     connected  = new AtomicBoolean(false);
    private final    BlockingQueue<String> messages = new LinkedBlockingQueue<>();
    /** True after a successful streamer initialisation. */
    volatile boolean streamerActive;

    // ── Constructor ───────────────────────────────────────────────────────

    public PiKvmWebSocketClient(PiKvmConfig config) {
        this(config, WebSocketRetryPolicy.DEFAULT, false, false);
    }

    public PiKvmWebSocketClient(PiKvmConfig config,
                                 WebSocketRetryPolicy retryPolicy,
                                 boolean activateStreamer,
                                 boolean extraVerbose) {
        this.config          = config;
        this.auth            = new AuthHeaderProvider(config);
        this.retryPolicy     = retryPolicy;
        this.activateStreamer = activateStreamer;
        this.extraVerbose    = extraVerbose;

        OkHttpClient.Builder b = new OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS); // no timeout for WebSocket
        if (!config.isCertificateTrusted()) {
            TlsHelper.trustAllCerts(b);
        }
        this.okHttp = b.build();

        this.ws = connect();
        this.streamerActive = activateStreamer;
    }

    /** Package-visible constructor for tests – injects a pre-built OkHttpClient. */
    PiKvmWebSocketClient(PiKvmConfig config,
                          OkHttpClient okHttp,
                          WebSocketRetryPolicy retryPolicy,
                          boolean activateStreamer,
                          boolean extraVerbose) {
        this.config          = config;
        this.auth            = new AuthHeaderProvider(config);
        this.retryPolicy     = retryPolicy;
        this.activateStreamer = activateStreamer;
        this.extraVerbose    = extraVerbose;
        this.okHttp          = okHttp;
        this.ws              = connect();
        this.streamerActive  = activateStreamer;
    }

    // ── Connection lifecycle ──────────────────────────────────────────────

    /**
     * Connects to PiKVM WebSocket with retry.
     * Equivalent to {@code _connect()} in Python.
     */
    private okhttp3.WebSocket connect() {
        String wsUrl = config.getWsBaseUrl() + "/api/ws?stream=" + (activateStreamer ? 1 : 0);
        LOGGER.fine("Connecting to " + wsUrl);

        Throwable lastError = null;
        for (int attempt = 0; attempt < retryPolicy.getMaxRetries(); attempt++) {
            try {
                return doConnect(wsUrl);
            } catch (Exception e) {
                lastError = e;
                LOGGER.info("WebSocket attempt " + (attempt + 1) + " failed: " + e.getMessage()
                            + ". Retrying in " + retryPolicy.getRetryDelayMs() + " ms…");
                try { Thread.sleep(retryPolicy.getRetryDelayMs()); } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        throw new PiKvmNetworkException(
            "Failed to connect to WebSocket after " + retryPolicy.getMaxRetries() + " attempts",
            lastError instanceof Exception ? (Exception) lastError : new RuntimeException(lastError));
    }

    private okhttp3.WebSocket doConnect(String wsUrl) throws InterruptedException {
        CountDownLatch latch     = new CountDownLatch(1);
        Throwable[]    errHolder = {null};
        connected.set(false);

        Request.Builder rb = new Request.Builder().url(wsUrl);
        auth.getHeaders().forEach(rb::addHeader);

        okhttp3.WebSocket newWs = okHttp.newWebSocket(rb.build(), new WebSocketListener() {
            @Override
            public void onOpen(okhttp3.WebSocket s, Response r) {
                connected.set(true);
                latch.countDown();
                LOGGER.fine("WebSocket connected to " + wsUrl);
            }
            @Override
            public void onMessage(okhttp3.WebSocket s, String text) {
                messages.add(text);
            }
            @Override
            public void onMessage(okhttp3.WebSocket s, ByteString bytes) {
                messages.add(bytes.utf8());
            }
            @Override
            public void onClosed(okhttp3.WebSocket s, int code, String reason) {
                connected.set(false);
            }
            @Override
            public void onFailure(okhttp3.WebSocket s, Throwable t, Response r) {
                connected.set(false);
                errHolder[0] = t;
                latch.countDown();
            }
        });

        boolean opened = latch.await(10, TimeUnit.SECONDS);
        if (!opened || !connected.get()) {
            newWs.cancel();
            Throwable err = errHolder[0];
            throw new PiKvmNetworkException(
                "WebSocket did not open within timeout",
                err instanceof Exception ? (Exception) err : err != null ? new RuntimeException(err) : null);
        }
        return newWs;
    }

    /**
     * Ensures the WebSocket is alive; reconnects if needed.
     * Equivalent to {@code _ensure_connection()} in Python.
     *
     * @return {@code true} if already connected, {@code false} if a reconnect was performed
     */
    public boolean ensureConnection() {
        if (connected.get()) return true;
        LOGGER.info("WebSocket lost – reconnecting…");
        try { ws.cancel(); } catch (Exception ignored) {}
        ws = connect();
        return false;
    }

    /**
     * Sends a WebSocket text frame with retry and reconnect logic.
     * Equivalent to {@code _send_with_retry(event_str)} in Python.
     */
    public void sendWithRetry(String message) {
        for (int attempt = 0; attempt < retryPolicy.getMaxRetries(); attempt++) {
            try {
                ensureConnection();
                boolean enqueued = ws.send(message);
                if (enqueued) return;
                throw new PiKvmNetworkException("WebSocket send rejected (queue full or closed)");
            } catch (PiKvmNetworkException e) {
                if (attempt + 1 >= retryPolicy.getMaxRetries()) {
                    LOGGER.severe("Failed to send after " + retryPolicy.getMaxRetries() + " attempts");
                    throw e;
                }
                LOGGER.info("Send attempt " + (attempt + 1) + " failed: " + e.getMessage()
                            + ". Retrying in " + retryPolicy.getRetryDelayMs() + " ms…");
                try { Thread.sleep(retryPolicy.getRetryDelayMs()); } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
                try { ws.cancel(); } catch (Exception ignored) {}
                ws = connect();
            }
        }
    }

    // ── Message reception ─────────────────────────────────────────────────

    /**
     * Blocks until a JSON message arrives and returns it parsed as a {@link Map}.
     * Equivalent to {@code get_json_message()} in Python.
     *
     * @return parsed message map, or {@code null} if JSON parsing fails
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getJsonMessage() throws InterruptedException {
        String raw = messages.take();
        try {
            return gson.fromJson(raw, Map.class);
        } catch (JsonSyntaxException e) {
            LOGGER.fine("Failed to parse JSON message: " + e.getMessage() + " Raw: " + raw);
            return null;
        }
    }

    /** Peek at one raw text message (blocks until available). */
    public String recv() throws InterruptedException {
        return messages.take();
    }

    // ── Keyboard event helpers ────────────────────────────────────────────

    /**
     * Creates a keyboard event JSON string.
     * Equivalent to {@code _create_event(key, state)} in Python.
     */
    public String createEvent(String key, boolean pressed) {
        String state = pressed ? "true" : "false";
        String event = String.format("{\"event_type\": \"key\", \"event\": {\"key\": \"%s\", \"state\": %s}}", key, state);
        if (extraVerbose) LOGGER.fine(event);
        return event;
    }

    /**
     * Sends key-down then key-up with a 50 ms gap.
     * Equivalent to {@code send_key(key)} in Python.
     */
    public void sendKey(String key) {
        sendWithRetry(createEvent(key, true));
        sleep(50);
        sendWithRetry(createEvent(key, false));
        if (extraVerbose) LOGGER.fine("Key: " + key + " sent");
        sleep(1);
    }

    /**
     * Sends a single key press or release event.
     * Equivalent to {@code send_key_press(key, action)} in Python.
     */
    public void sendKeyPress(String key, boolean pressed) {
        sendWithRetry(createEvent(key, pressed));
        sleep(50);
        if (extraVerbose) LOGGER.fine("Key: " + key + " " + (pressed ? "pressed" : "released"));
        sleep(1);
    }

    /**
     * Sends Ctrl+Alt+Delete.
     * Equivalent to {@code send_ctrl_alt_sup()} in Python.
     */
    public void sendCtrlAltDel() {
        sendWithRetry(createEvent("ControlLeft", true));  sleep(50);
        sendWithRetry(createEvent("AltLeft",     true));  sleep(50);
        sendWithRetry(createEvent("Delete",       true));  sleep(50);
        sendWithRetry(createEvent("ControlLeft", false)); sleep(50);
        sendWithRetry(createEvent("AltLeft",     false)); sleep(50);
        sendWithRetry(createEvent("Delete",       false)); sleep(50);
    }

    /** Sends a key with Shift held (for uppercase / special chars). */
    public void sendShiftKey(String key) {
        sendWithRetry(createEvent("ShiftLeft", true));
        sendKey(key);
        sendWithRetry(createEvent("ShiftLeft", false));
    }

    private void sendStandardKeys(String key) {
        if (Character.isUpperCase(key.charAt(0))) {
            sendShiftKey("Key" + key.toUpperCase());
        } else if (Character.isDigit(key.charAt(0))) {
            sendKey("Digit" + key);
        } else if (" ".equals(key)) {
            sendKey("Space");
        } else {
            sendKey("Key" + key.toUpperCase());
        }
    }

    private void sendExtraKey(String key) {
        String code = Keymaps.BASE.getOrDefault(key, null);
        if (code != null) {
            sendKey(code);
            return;
        }
        // Check shift map
        if ("\"".equals(key)) {
            sendShiftKey("Quote");
            return;
        }
        String shiftCode = Keymaps.SHIFT.getOrDefault(key, null);
        if (shiftCode != null) {
            sendShiftKey(shiftCode);
            return;
        }
        LOGGER.fine("Unrecognised key: " + key);
    }

    /**
     * Sends a full text string character-by-character, handling special key syntax.
     * Equivalent to {@code send_input(text)} in Python.
     */
    public void sendInput(String text) {
        LOGGER.fine("Sending input: " + text);
        // Detect special tags like <ArrowUp>
        Pattern p = Pattern.compile("<(\\w+)>");
        java.util.List<Matcher> matches = new java.util.ArrayList<>();
        {
            Matcher m = p.matcher(text);
            while (m.find()) {
                String tag = m.group();
                if (Keymaps.BASE.values().stream().anyMatch(v -> tag.contains(v))
                        || Keymaps.BASE.containsKey(tag)) {
                    matches.add(m.toMatchResult() instanceof Matcher ? (Matcher) m.toMatchResult() : null);
                    // Re-create matcher at same position to get fresh Matcher state
                }
            }
        }
        // Re-do: collect MatchResult list properly
        java.util.List<java.util.regex.MatchResult> found = new java.util.ArrayList<>();
        {
            Matcher m = p.matcher(text);
            while (m.find()) {
                if (Keymaps.BASE.containsKey(m.group())) {
                    found.add(m.toMatchResult());
                }
            }
        }

        int i = 0;
        int foundIdx = 0;
        while (i < text.length()) {
            if (foundIdx < found.size() && found.get(foundIdx).start() == i) {
                java.util.regex.MatchResult mr = found.get(foundIdx);
                sendKey(mr.group(1));
                i = mr.end();
                foundIdx++;
            } else {
                String key = String.valueOf(text.charAt(i));
                if (key.matches("[a-zA-Z0-9 ]")) {
                    sendStandardKeys(key);
                } else {
                    sendExtraKey(key);
                }
                i++;
            }
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────

    @Override
    public void close() {
        if (ws != null) {
            ws.close(1000, "Normal closure");
            connected.set(false);
        }
    }

    public boolean isConnected()      { return connected.get(); }
    public boolean isStreamerActive()  { return streamerActive; }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
