package org.pikvm.streamer;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import okhttp3.Response;
import org.pikvm.endpoint.BaseEndpoint;
import org.pikvm.exception.PiKvmNetworkException;
import org.pikvm.exception.PiKvmStreamException;
import org.pikvm.http.PiKvmHttpClient;
import org.pikvm.websocket.PiKvmWebSocketClient;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Streamer client for screen capture.
 * <p>
 * Equivalent to {@code PiKVMStreamer} in Python. Provides snapshot capture
 * as raw bytes (platform-agnostic) and optional file-based snapshots.
 * </p>
 */
public class StreamerClient extends BaseEndpoint {

    private static final Logger LOGGER          = Logger.getLogger(StreamerClient.class.getName());
    private static final String STREAMER_PATH   = "/api/streamer";
    private static final String SNAPSHOT_PATH   = "/api/streamer/snapshot";
    private static final Gson   GSON            = new Gson();

    private PiKvmWebSocketClient wsClient;

    public StreamerClient(PiKvmHttpClient httpClient, PiKvmWebSocketClient wsClient) {
        super(httpClient);
        this.wsClient = wsClient;
    }

    // ── State ────────────────────────────────────────────────────────────

    /**
     * Returns the streamer subsystem state.
     * Equivalent to {@code get_streamer_state()}.
     */
    public Map<String, Object> getStreamerState() {
        return getEndpointState(STREAMER_PATH);
    }

    // ── Streamer init ────────────────────────────────────────────────────

    /**
     * Waits until the streamer signals it is online.
     * Equivalent to {@code _await_streamer_initialization()}.
     *
     * @param timeoutMs maximum wait time in milliseconds
     * @return {@code true} if initialised within timeout, {@code false} otherwise
     */
    public boolean awaitStreamerInitialization(long timeoutMs) {
        LOGGER.info("Awaiting streamer initialisation (timeout " + timeoutMs + " ms)…");
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            try {
                Map<String, Object> msg = wsClient.getJsonMessage();
                if (isStreamerOnline(msg)) {
                    return true;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            } catch (Exception e) {
                LOGGER.fine("Error processing streamer message: " + e.getMessage());
            }
        }
        LOGGER.warning("Streamer initialisation timed out after " + timeoutMs + " ms");
        return false;
    }

    /**
     * Ensures the streamer WebSocket is started and online.
     * Equivalent to {@code _ensure_streamer_started()}.
     */
    public void ensureStreamerStarted(java.util.function.Supplier<PiKvmWebSocketClient> wsFactory) {
        if (wsClient != null && !wsClient.isStreamerActive()) {
            wsClient.close();
            wsClient = null;
        }
        if (wsClient == null) {
            LOGGER.info("Starting new streamer-enabled WebSocket");
            wsClient = wsFactory.get();
            boolean ok = awaitStreamerInitialization(30_000);
            if (!ok) {
                throw new PiKvmStreamException("Failed to initialise streamer");
            }
        } else {
            boolean wasConnected = wsClient.ensureConnection();
            if (!wasConnected) {
                boolean ok = awaitStreamerInitialization(30_000);
                if (!ok) throw new PiKvmStreamException("Failed to re-initialise streamer");
            }
        }
    }

    /** Attaches (or replaces) the WebSocket client used for streamer events. */
    public void setWsClient(PiKvmWebSocketClient wsClient) {
        this.wsClient = wsClient;
    }

    public PiKvmWebSocketClient getWsClient() { return wsClient; }

    // ── Snapshot ─────────────────────────────────────────────────────────

    /**
     * Captures a snapshot and returns raw JPEG bytes (or OCR text bytes).
     * Equivalent to the core logic of {@code get_streamer_snapshot()} and
     * {@code get_streamer_image()} in Python.
     *
     * @param ocr        request OCR text instead of image bytes
     * @param maxRetries retry attempts if streamer returns invalid response
     * @return {@link SnapshotResult} containing raw bytes
     */
    public SnapshotResult captureSnapshot(boolean ocr, int maxRetries) {
        String options = ocr ? "ocr=1&allow_offline=1" : "allow_offline=1";
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try (Response resp = httpClient.get(SNAPSHOT_PATH, options)) {
                if (!validateStreamerResponse(resp)) {
                    LOGGER.info("Invalid streamer response, retrying ("
                                + (maxRetries - attempt) + " attempts left)");
                    continue;
                }
                byte[] bytes = resp.body() == null ? new byte[0] : resp.body().bytes();
                return ocr ? SnapshotResult.ofOcr(bytes) : SnapshotResult.ofImage(bytes);
            } catch (IOException e) {
                if (attempt >= maxRetries) throw new PiKvmNetworkException("Snapshot failed", e);
                LOGGER.fine("Snapshot attempt " + (attempt + 1) + " failed: " + e.getMessage());
            }
        }
        throw new PiKvmStreamException("Maximum retries exceeded when getting streamer snapshot");
    }

    /** Convenience overload: 3 retries, no OCR. */
    public SnapshotResult captureSnapshot() {
        return captureSnapshot(false, 3);
    }

    /**
     * Captures a snapshot and writes it to a file.
     * Equivalent to {@code get_streamer_snapshot(snapshot_path, filename, ocr)} in Python.
     *
     * @param outputFile destination file
     * @param ocr        request OCR mode
     * @return the output file
     */
    public File captureSnapshotToFile(File outputFile, boolean ocr) {
        SnapshotResult result = captureSnapshot(ocr, 3);
        try (FileOutputStream fos = new FileOutputStream(outputFile)) {
            fos.write(result.getImageBytes());
        } catch (IOException e) {
            throw new PiKvmNetworkException("Failed to write snapshot to file", e);
        }
        LOGGER.info("Wrote snapshot to: " + outputFile.getAbsolutePath());
        return outputFile;
    }

    // ── Internal ─────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private boolean validateStreamerResponse(Response resp) {
        if (resp.body() == null) return false;
        try {
            String body = resp.peekBody(Long.MAX_VALUE).string();
            if (body.trim().startsWith("{")) {
                Map<String, Object> json = GSON.fromJson(body,
                    new TypeToken<Map<String, Object>>() {}.getType());
                Object result = json.get("result");
                if (result instanceof Map && ((Map<String, Object>) result).containsKey("error")) {
                    return false;
                }
            }
        } catch (Exception ignored) {}
        return true;
    }

    @SuppressWarnings("unchecked")
    private static boolean isStreamerOnline(Map<String, Object> msg) {
        if (msg == null) return false;
        if (!"streamer".equals(msg.get("event_type"))) return false;
        try {
            Map<String, Object> event    = (Map<String, Object>) msg.get("event");
            Map<String, Object> streamer = (Map<String, Object>) event.get("streamer");
            Map<String, Object> source   = (Map<String, Object>) streamer.get("source");
            return Boolean.TRUE.equals(source.get("online"));
        } catch (Exception e) {
            return false;
        }
    }
}
