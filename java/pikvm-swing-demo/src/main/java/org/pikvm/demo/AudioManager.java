package org.pikvm.demo;

import org.pikvm.http.PiKvmHttpClient;

import javax.sound.sampled.*;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manages remote audio playback and local microphone streaming for PiKVM.
 *
 * <p><strong>Speaker (remote → local):</strong>
 * Connects to {@code /api/streamer/audio} on the PiKVM device and plays the
 * received raw PCM stream through the local sound system.  If the device does
 * not expose a raw audio endpoint (WebRTC-only configurations), the attempt
 * fails gracefully and a status callback is invoked so the UI can display an
 * informative message.</p>
 *
 * <p><strong>Microphone (local → remote):</strong>
 * Captures audio from the default system microphone and POSTs a raw PCM stream
 * to {@code /api/streamer/audio} on the PiKVM device.  Same graceful fallback
 * applies.</p>
 *
 * <p>Audio format used: <em>PCM signed 16-bit, 48 000 Hz, mono, little-endian</em>.
 * This matches the format expected / produced by most modern PiKVM audio builds.</p>
 */
class AudioManager implements AutoCloseable {

    private static final Logger LOGGER = Logger.getLogger(AudioManager.class.getName());

    /** Raw-audio stream endpoint. Present only on PiKVM builds with audio support. */
    private static final String AUDIO_PATH  = "/api/streamer/audio";
    /** PCM format: 48 kHz, 16-bit signed, mono, little-endian. */
    static final AudioFormat    AUDIO_FMT   =
            new AudioFormat(48_000, 16, 1, true, false);
    private static final int    CHUNK_BYTES = 4096;   // read/write chunk

    // ── Status callback ───────────────────────────────────────────────────

    @FunctionalInterface
    interface StatusListener {
        /**
         * Called when the audio state changes.
         *
         * @param kind    one of {@code "speaker"} or {@code "mic"}
         * @param message human-readable status (may describe an error)
         * @param ok      {@code true} if the component is running normally
         */
        void onStatus(String kind, String message, boolean ok);
    }

    // ── State ──────────────────────────────────────────────────────────────

    private final PiKvmHttpClient httpClient;
    private final StatusListener  statusListener;
    private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "pikvm-audio");
        t.setDaemon(true);
        return t;
    });

    private final AtomicBoolean speakerRunning = new AtomicBoolean(false);
    private final AtomicBoolean micRunning     = new AtomicBoolean(false);

    /** Speaker volume: 0–100 (mapped to SourceDataLine gain). */
    private final AtomicInteger speakerVolume  = new AtomicInteger(80);
    /** Microphone gain: 0–100. */
    private final AtomicInteger micVolume      = new AtomicInteger(80);

    private volatile Future<?> speakerFuture;
    private volatile Future<?> micFuture;
    private volatile SourceDataLine speakerLine;
    private volatile TargetDataLine micLine;

    /** Latest RMS level [0.0 – 1.0] for the level-meter UI. */
    volatile float speakerLevel = 0f;
    volatile float micLevel     = 0f;

    // ── Constructor ────────────────────────────────────────────────────────

    AudioManager(PiKvmHttpClient httpClient, StatusListener statusListener) {
        this.httpClient     = httpClient;
        this.statusListener = statusListener;
    }

    // ── Speaker control ───────────────────────────────────────────────────

    /** Starts remote audio playback. Non-blocking; runs in a background thread. */
    void startSpeaker() {
        if (!speakerRunning.compareAndSet(false, true)) return;
        speakerFuture = executor.submit(this::speakerLoop);
    }

    /** Stops remote audio playback. */
    void stopSpeaker() {
        speakerRunning.set(false);
        SourceDataLine l = speakerLine;
        if (l != null) { l.stop(); l.close(); }
        Future<?> f = speakerFuture;
        if (f != null) f.cancel(true);
        speakerLevel = 0f;
        notifyStatus("speaker", "Stopped", false);
    }

    boolean isSpeakerRunning() { return speakerRunning.get(); }

    /** Sets speaker volume 0–100. */
    void setSpeakerVolume(int pct) {
        speakerVolume.set(Math.max(0, Math.min(100, pct)));
        applyGain(speakerLine, speakerVolume.get());
    }

    // ── Microphone control ─────────────────────────────────────────────────

    /** Starts local microphone capture and streaming to PiKVM. Non-blocking. */
    void startMic() {
        if (!micRunning.compareAndSet(false, true)) return;
        micFuture = executor.submit(this::micLoop);
    }

    /** Stops microphone capture. */
    void stopMic() {
        micRunning.set(false);
        TargetDataLine l = micLine;
        if (l != null) { l.stop(); l.close(); }
        Future<?> f = micFuture;
        if (f != null) f.cancel(true);
        micLevel = 0f;
        notifyStatus("mic", "Stopped", false);
    }

    boolean isMicRunning() { return micRunning.get(); }

    /** Sets microphone gain 0–100. */
    void setMicVolume(int pct) {
        micVolume.set(Math.max(0, Math.min(100, pct)));
    }

    // ── AutoCloseable ─────────────────────────────────────────────────────

    @Override
    public void close() {
        stopSpeaker();
        stopMic();
        executor.shutdownNow();
    }

    // ── Speaker loop ───────────────────────────────────────────────────────

    private void speakerLoop() {
        notifyStatus("speaker", "Connecting…", false);
        try {
            SourceDataLine line = openOutputLine();
            speakerLine = line;
            applyGain(line, speakerVolume.get());
            line.start();
            notifyStatus("speaker", "Streaming", true);

            try (var resp = httpClient.get(AUDIO_PATH)) {
                if (!resp.isSuccessful() || resp.body() == null) {
                    notifyStatus("speaker",
                            "Audio endpoint returned " + resp.code()
                            + ". Device may use WebRTC-only audio.", false);
                    return;
                }
                streamAudioToLine(resp.body().byteStream(), line);
            }
        } catch (LineUnavailableException e) {
            notifyStatus("speaker", "No audio output device available: " + e.getMessage(), false);
        } catch (IOException e) {
            if (speakerRunning.get()) {
                notifyStatus("speaker", "Stream error: " + e.getMessage(), false);
            }
        } finally {
            speakerRunning.set(false);
            SourceDataLine l = speakerLine;
            if (l != null) { l.drain(); l.close(); }
        }
    }

    private void streamAudioToLine(InputStream in, SourceDataLine line) throws IOException {
        byte[] buf = new byte[CHUNK_BYTES];
        while (speakerRunning.get() && !Thread.currentThread().isInterrupted()) {
            int n = in.read(buf);
            if (n == -1) break;
            if (n > 0) {
                // Apply software volume scaling
                applyVolume(buf, n, speakerVolume.get());
                speakerLevel = rmsLevel(buf, n);
                line.write(buf, 0, n);
            }
        }
    }

    private static SourceDataLine openOutputLine() throws LineUnavailableException {
        DataLine.Info info = new DataLine.Info(SourceDataLine.class, AUDIO_FMT);
        SourceDataLine line = (SourceDataLine) AudioSystem.getLine(info);
        line.open(AUDIO_FMT, CHUNK_BYTES * 4);
        return line;
    }

    // ── Microphone loop ───────────────────────────────────────────────────

    private void micLoop() {
        notifyStatus("mic", "Connecting…", false);
        try {
            TargetDataLine line = openInputLine();
            micLine = line;
            line.start();
            notifyStatus("mic", "Streaming", true);

            streamMicToRemote(line);
        } catch (LineUnavailableException e) {
            notifyStatus("mic", "No microphone available: " + e.getMessage(), false);
        } catch (IOException e) {
            if (micRunning.get()) {
                notifyStatus("mic", "Stream error: " + e.getMessage(), false);
            }
        } finally {
            micRunning.set(false);
            TargetDataLine l = micLine;
            if (l != null) { l.stop(); l.close(); }
        }
    }

    private void streamMicToRemote(TargetDataLine line) throws IOException {
        byte[] buf = new byte[CHUNK_BYTES];

        // Use OkHttp directly via httpClient's underlying client for streaming POST
        // We open the connection and write audio chunks until mic is stopped.
        okhttp3.OkHttpClient okHttp = httpClient.getOkHttpClient();
        okhttp3.MediaType pcm = okhttp3.MediaType.parse("audio/pcm");

        // Build a streaming request body backed by the microphone data
        okhttp3.RequestBody body = new okhttp3.RequestBody() {
            @Override public okhttp3.MediaType contentType() { return pcm; }
            @Override public long contentLength() { return -1; }
            @Override public void writeTo(okio.BufferedSink sink) throws IOException {
                byte[] chunk = new byte[CHUNK_BYTES];
                while (micRunning.get() && !Thread.currentThread().isInterrupted()) {
                    int n = line.read(chunk, 0, chunk.length);
                    if (n > 0) {
                        applyVolume(chunk, n, micVolume.get());
                        micLevel = rmsLevel(chunk, n);
                        sink.write(chunk, 0, n);
                        sink.flush();
                    }
                }
            }
        };

        // Build request with auth headers from the HTTP client config
        okhttp3.Request.Builder rb = new okhttp3.Request.Builder()
                .url(httpClient.getConfig().getBaseUrl() + AUDIO_PATH)
                .post(body);
        // Inject auth headers via reflection on the existing HTTP client
        // (AuthHeaderProvider is package-private; we re-use the HttpClient's headers approach)
        addAuthHeaders(rb);

        try (var resp = okHttp.newCall(rb.build()).execute()) {
            if (!resp.isSuccessful()) {
                notifyStatus("mic",
                        "Mic endpoint returned " + resp.code()
                        + ". Device may use WebRTC-only audio.", false);
            }
        }
    }

    /**
     * Adds Basic-auth header directly from the config credentials.
     * This mirrors what {@link org.pikvm.auth.AuthHeaderProvider} does.
     */
    private void addAuthHeaders(okhttp3.Request.Builder rb) {
        var cfg = httpClient.getConfig();
        String credentials = cfg.getUsername() + ":" + cfg.getPassword();
        String encoded = java.util.Base64.getEncoder()
                .encodeToString(credentials.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        rb.header("Authorization", "Basic " + encoded);
        rb.header("X-KVMD-User",   cfg.getUsername());
        rb.header("X-KVMD-Passwd", cfg.getPassword());
    }

    private static TargetDataLine openInputLine() throws LineUnavailableException {
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, AUDIO_FMT);
        TargetDataLine line = (TargetDataLine) AudioSystem.getLine(info);
        line.open(AUDIO_FMT, CHUNK_BYTES * 4);
        return line;
    }

    // ── Audio helpers ──────────────────────────────────────────────────────

    /** Scales each 16-bit PCM sample by {@code volumePct} / 100. */
    private static void applyVolume(byte[] buf, int len, int volumePct) {
        if (volumePct == 100) return;
        float scale = volumePct / 100f;
        for (int i = 0; i + 1 < len; i += 2) {
            short sample = (short) ((buf[i + 1] << 8) | (buf[i] & 0xFF));
            sample = (short) (sample * scale);
            buf[i]     = (byte) (sample & 0xFF);
            buf[i + 1] = (byte) ((sample >> 8) & 0xFF);
        }
    }

    /** Computes RMS level in [0, 1] from 16-bit signed PCM samples. */
    private static float rmsLevel(byte[] buf, int len) {
        long sumSq = 0;
        int samples = 0;
        for (int i = 0; i + 1 < len; i += 2) {
            short s = (short) ((buf[i + 1] << 8) | (buf[i] & 0xFF));
            sumSq += (long) s * s;
            samples++;
        }
        if (samples == 0) return 0f;
        return (float) Math.sqrt((double) sumSq / samples) / Short.MAX_VALUE;
    }

    /** Applies a software gain control on the line if the control is available. */
    private static void applyGain(SourceDataLine line, int volumePct) {
        if (line == null || !line.isOpen()) return;
        if (line.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
            FloatControl gain = (FloatControl) line.getControl(FloatControl.Type.MASTER_GAIN);
            float dB = 20f * (float) Math.log10(Math.max(0.0001, volumePct / 100.0));
            dB = Math.max(gain.getMinimum(), Math.min(gain.getMaximum(), dB));
            gain.setValue(dB);
        }
    }

    private void notifyStatus(String kind, String msg, boolean ok) {
        LOGGER.log(ok ? Level.INFO : Level.WARNING, "Audio [" + kind + "]: " + msg);
        if (statusListener != null) statusListener.onStatus(kind, msg, ok);
    }
}
