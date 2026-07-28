package org.pikvm.demo;

import okhttp3.Response;
import org.pikvm.http.PiKvmHttpClient;

import java.io.DataInputStream;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

/**
 * Reads a PiKVM MJPEG multipart stream and delivers decoded JPEG frames.
 *
 * <p>Connects to {@code /api/streamer/stream?type=mjpeg&desired_fps=30} and
 * continuously parses the multipart response body. Each JPEG frame is
 * delivered as raw bytes to a {@link FrameListener}. The receiver
 * automatically reconnects on network errors.</p>
 *
 * <p>Usage:
 * <pre>{@code
 * MjpegStreamReceiver rx = new MjpegStreamReceiver();
 * rx.start(client.getHttpClient(), (jpeg, w, h) -> {
 *     BufferedImage img = ImageIO.read(new ByteArrayInputStream(jpeg));
 *     panel.setImage(img);
 * });
 * // later:
 * rx.stop();
 * }</pre>
 * </p>
 */
class MjpegStreamReceiver {

    private static final Logger LOGGER       = Logger.getLogger(MjpegStreamReceiver.class.getName());
    private static final String STREAM_PATH  = "/api/streamer/stream";
    private static final String STREAM_QUERY = "type=mjpeg&desired_fps=30&quality=85";
    /** Reconnect delay after a stream error (ms). */
    private static final int    RETRY_MS     = 1500;

    // ── Public interface ──────────────────────────────────────────────────

    /**
     * Callback interface for decoded MJPEG frames.
     */
    @FunctionalInterface
    interface FrameListener {
        /**
         * Called on the streaming thread for each decoded JPEG frame.
         *
         * @param jpegBytes raw JPEG bytes
         * @param width     image width in pixels (from JPEG SOF header)
         * @param height    image height in pixels
         */
        void onFrame(byte[] jpegBytes, int width, int height);
    }

    // ── State ──────────────────────────────────────────────────────────────

    private final AtomicBoolean running    = new AtomicBoolean(false);
    private final AtomicLong    frameCount = new AtomicLong(0);
    private volatile double     currentFps = 0;
    private volatile Thread     streamThread;

    // ── Lifecycle ──────────────────────────────────────────────────────────

    /**
     * Starts the background streaming thread.
     *
     * @param httpClient authenticated HTTP client from {@link org.pikvm.PiKvmClient}
     * @param listener   frame callback
     */
    void start(PiKvmHttpClient httpClient, FrameListener listener) {
        if (!running.compareAndSet(false, true)) return;
        frameCount.set(0);
        currentFps = 0;

        streamThread = new Thread(() -> loop(httpClient, listener), "mjpeg-stream");
        streamThread.setDaemon(true);
        streamThread.start();
    }

    /** Stops the streaming thread and closes any open connection. */
    void stop() {
        running.set(false);
        Thread t = streamThread;
        if (t != null) {
            t.interrupt();
            streamThread = null;
        }
    }

    /** Returns the measured frames-per-second over the last second. */
    double getCurrentFps() { return currentFps; }

    /** Returns the total number of frames received since {@link #start}. */
    long getFrameCount() { return frameCount.get(); }

    /** Returns {@code true} while the streaming thread is running. */
    boolean isRunning() { return running.get(); }

    // ── Private: outer loop with reconnect ────────────────────────────────

    private void loop(PiKvmHttpClient httpClient, FrameListener listener) {
        while (running.get()) {
            try {
                connect(httpClient, listener);
            } catch (IOException e) {
                if (running.get()) {
                    LOGGER.warning("MJPEG stream error, reconnecting in " + RETRY_MS + " ms: " + e.getMessage());
                    sleep(RETRY_MS);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        LOGGER.info("MJPEG stream stopped");
    }

    // ── Private: single connection ─────────────────────────────────────────

    private void connect(PiKvmHttpClient httpClient, FrameListener listener)
            throws IOException, InterruptedException {

        LOGGER.info("Connecting to MJPEG stream…");
        try (Response resp = httpClient.get(STREAM_PATH, STREAM_QUERY)) {
            if (!resp.isSuccessful() || resp.body() == null) {
                LOGGER.warning("MJPEG stream returned HTTP " + resp.code());
                sleep(RETRY_MS);
                return;
            }
            String boundary = parseBoundary(resp.header("Content-Type", ""));
            parse(new DataInputStream(new BufferedInputStream(resp.body().byteStream(), 65_536)),
                  boundary, listener);
        }
    }

    // ── Private: multipart MJPEG parser ───────────────────────────────────

    private void parse(DataInputStream in, String boundary, FrameListener listener)
            throws IOException, InterruptedException {

        long windowStart  = System.currentTimeMillis();
        long windowFrames = 0;

        while (running.get() && !Thread.currentThread().isInterrupted()) {

            // ── 1. Skip until boundary line ──────────────────────────────
            String line;
            do {
                line = readLine(in);
                if (line == null) return;  // stream closed
            } while (!(line.contains("--") || (!boundary.isEmpty() && line.contains(boundary))));

            // ── 2. Read MIME headers ─────────────────────────────────────
            int contentLength = -1;
            while (true) {
                line = readLine(in);
                if (line == null) return;
                if (line.isEmpty()) break;                              // blank line = end of headers
                if (line.toLowerCase().startsWith("content-length:")) {
                    try {
                        contentLength = Integer.parseInt(line.substring(line.indexOf(':') + 1).trim());
                    } catch (NumberFormatException ignore) { /* fall through */ }
                }
            }

            // ── 3. Read JPEG payload ─────────────────────────────────────
            if (contentLength <= 0) continue;   // skip malformed parts
            byte[] jpeg = new byte[contentLength];
            in.readFully(jpeg);

            // ── 4. Validate JPEG magic bytes ─────────────────────────────
            if (jpeg.length < 4
                    || (jpeg[0] & 0xFF) != 0xFF
                    || (jpeg[1] & 0xFF) != 0xD8) continue;

            // ── 5. Deliver frame ──────────────────────────────────────────
            int[] dims = jpegDimensions(jpeg);
            listener.onFrame(jpeg, dims[0], dims[1]);

            // ── 6. Update FPS counter ─────────────────────────────────────
            windowFrames++;
            frameCount.incrementAndGet();
            long now     = System.currentTimeMillis();
            long elapsed = now - windowStart;
            if (elapsed >= 1000) {
                currentFps   = windowFrames * 1000.0 / elapsed;
                windowStart  = now;
                windowFrames = 0;
            }
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    /**
     * Reads one CRLF or LF terminated line from {@code dis}.
     * Returns {@code null} if the stream is exhausted.
     */
    private static String readLine(DataInputStream dis) throws IOException {
        StringBuilder sb = new StringBuilder(64);
        int b;
        while ((b = dis.read()) != -1) {
            if (b == '\n') {
                int len = sb.length();
                if (len > 0 && sb.charAt(len - 1) == '\r') sb.deleteCharAt(len - 1);
                return sb.toString();
            }
            sb.append((char) b);
        }
        return sb.length() > 0 ? sb.toString() : null;
    }

    private static String parseBoundary(String contentType) {
        if (contentType == null) return "";
        for (String part : contentType.split(";")) {
            String t = part.trim();
            if (t.toLowerCase().startsWith("boundary=")) {
                return t.substring("boundary=".length()).replaceAll("^\"|\"$", "").trim();
            }
        }
        return "";
    }

    /** Minimal JPEG SOF0/SOF1/SOF2 parser to extract width and height. */
    static int[] jpegDimensions(byte[] jpeg) {
        for (int i = 0; i < jpeg.length - 9; i++) {
            if ((jpeg[i] & 0xFF) == 0xFF) {
                int marker = jpeg[i + 1] & 0xFF;
                if (marker == 0xC0 || marker == 0xC1 || marker == 0xC2) {
                    int h = ((jpeg[i + 5] & 0xFF) << 8) | (jpeg[i + 6] & 0xFF);
                    int w = ((jpeg[i + 7] & 0xFF) << 8) | (jpeg[i + 8] & 0xFF);
                    if (w > 0 && h > 0) return new int[]{w, h};
                }
            }
        }
        return new int[]{1920, 1080};
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
