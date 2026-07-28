package org.pikvm.demo;

import org.pikvm.PiKvmClient;
import org.pikvm.mouse.MouseButton;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

/**
 * Remote desktop panel: displays the live MJPEG stream from a PiKVM device
 * and forwards all mouse and keyboard events back via WebSocket.
 *
 * <h3>Video</h3>
 * <p>Frames are delivered by {@link MjpegStreamReceiver} on a background
 * thread.  Each JPEG frame is decoded and stored in an
 * {@link AtomicReference}; a Swing timer repaints at the display refresh
 * rate so the EDT is never blocked by decoding.</p>
 *
 * <h3>Mouse control</h3>
 * <p>Mouse events captured by Swing are translated to PiKVM coordinates
 * (accounting for letterbox/pillarbox offsets) and dispatched via the
 * WebSocket input client.  Mouse moves are sent at up to 60 Hz using a
 * dedicated rate-limiting timer.</p>
 *
 * <h3>Keyboard control</h3>
 * <p>AWT key events are mapped to PiKVM DOM key names by
 * {@link AwtKeyMapper} and forwarded as raw WebSocket events. Focus
 * traversal is disabled so Tab and Shift+Tab are forwarded to the remote
 * machine rather than moving focus between Swing components.</p>
 */
class RemoteDesktopPanel extends JPanel
        implements MouseListener, MouseMotionListener, MouseWheelListener, KeyListener {

    private static final Logger LOGGER = Logger.getLogger(RemoteDesktopPanel.class.getName());

    // ── State ──────────────────────────────────────────────────────────────

    private volatile PiKvmClient client;

    /** Latest decoded frame, updated on the streaming thread. */
    private final AtomicReference<BufferedImage> latestFrame = new AtomicReference<>();
    /** Remote screen resolution (set when first frame arrives). */
    private volatile int remoteW = 1920;
    private volatile int remoteH = 1080;

    /** Current FPS from the stream receiver. */
    private volatile double streamFps = 0;
    /** Whether the MJPEG stream is active. */
    private volatile boolean streamActive = false;

    /**
     * Pending mouse coordinates to send, set by mouse-move listener,
     * consumed by the rate-limiting timer.
     */
    private volatile int pendingMouseX = -1;
    private volatile int pendingMouseY = -1;
    private volatile boolean mousePending = false;

    /**
     * Single-threaded executor for all input events sent to PiKVM.
     * Keeps ordering and prevents blocking the EDT.
     */
    private final ExecutorService inputSender = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "pikvm-input");
        t.setDaemon(true);
        return t;
    });

    // ── Timers ─────────────────────────────────────────────────────────────

    /** Repaint timer: ~60 FPS (16 ms) so frame display feels fluid. */
    private final Timer repaintTimer;
    /** Mouse-move rate limiter: sends queued moves at most every 16 ms. */
    private final Timer mouseMoveTimer;

    // ── Constructor ────────────────────────────────────────────────────────

    RemoteDesktopPanel() {
        setBackground(Color.BLACK);
        setFocusable(true);
        setFocusTraversalKeysEnabled(false);   // Tab → remote, not Swing focus
        setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));

        addMouseListener(this);
        addMouseMotionListener(this);
        addMouseWheelListener(this);
        addKeyListener(this);

        // Request focus when clicked
        addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) { requestFocusInWindow(); }
        });

        repaintTimer   = new Timer(16, e -> repaint());
        mouseMoveTimer = new Timer(16, e -> flushMouseMove());
        repaintTimer.start();
        mouseMoveTimer.start();
    }

    // ── Client attachment ──────────────────────────────────────────────────

    /** Attaches (or replaces) the PiKVM client. May be called on any thread. */
    void setClient(PiKvmClient c) {
        this.client = c;
    }

    // ── Frame delivery ─────────────────────────────────────────────────────

    /**
     * Called by {@link MjpegStreamReceiver.FrameListener} on the streaming thread.
     * Decodes the JPEG and stores the image for painting.
     */
    void onFrame(byte[] jpegBytes, int width, int height) {
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(jpegBytes));
            if (img != null) {
                latestFrame.set(img);
                if (remoteW != width || remoteH != height) {
                    remoteW = width;
                    remoteH = height;
                    PiKvmClient c = client;
                    if (c != null) c.getMouse().setScreenDimensions(width, height);
                }
            }
        } catch (IOException e) {
            LOGGER.fine("Frame decode error: " + e.getMessage());
        }
    }

    /** Updates the displayed FPS value and stream-active flag. */
    void setStreamFps(double fps, boolean active) {
        streamFps    = fps;
        streamActive = active;
    }

    // ── Painting ────────────────────────────────────────────────────────────

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                            RenderingHints.VALUE_INTERPOLATION_BILINEAR);

        BufferedImage frame = latestFrame.get();
        int pw = getWidth(), ph = getHeight();

        if (frame == null) {
            // No frame yet: draw placeholder
            g2.setColor(Color.DARK_GRAY);
            g2.fillRect(0, 0, pw, ph);
            g2.setColor(Color.WHITE);
            g2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
            String msg = streamActive ? "Waiting for frames…" : "Stream not started";
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(msg, (pw - fm.stringWidth(msg)) / 2, ph / 2);
        } else {
            // Draw frame scaled to fit, preserving aspect ratio
            DrawBounds db = computeDrawBounds(pw, ph, frame.getWidth(), frame.getHeight());
            g2.drawImage(frame, db.x, db.y, db.w, db.h, null);
        }

        // FPS overlay
        g2.setColor(new Color(0, 0, 0, 140));
        g2.fillRect(4, 4, 110, 20);
        g2.setColor(streamFps >= 25 ? Color.GREEN : Color.YELLOW);
        g2.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        g2.drawString(String.format("FPS: %4.1f", streamFps), 8, 19);

        // Focus indicator
        if (hasFocus()) {
            g2.setColor(new Color(0, 120, 255, 180));
            g2.setStroke(new BasicStroke(2));
            g2.drawRect(1, 1, pw - 3, ph - 3);
        }
    }

    // ── Mouse events ─────────────────────────────────────────────────────

    @Override
    public void mouseMoved(MouseEvent e) {
        pendingMouseX = e.getX();
        pendingMouseY = e.getY();
        mousePending  = true;
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        pendingMouseX = e.getX();
        pendingMouseY = e.getY();
        mousePending  = true;
    }

    private void flushMouseMove() {
        if (!mousePending) return;
        int px = pendingMouseX;
        int py = pendingMouseY;
        mousePending = false;
        PiKvmClient c = client;
        if (c == null) return;

        int[] remote = panelToRemote(px, py);
        if (remote == null) return;
        inputSender.execute(() -> {
            try { c.getMouse().sendMouseMoveEvent(remote[0], remote[1]); }
            catch (Exception ex) { LOGGER.fine("Mouse move error: " + ex.getMessage()); }
        });
    }

    @Override
    public void mousePressed(MouseEvent e) {
        MouseButton btn = awtButtonToKvm(e.getButton());
        if (btn == null) return;
        PiKvmClient c = client;
        if (c == null) return;
        inputSender.execute(() -> {
            try { c.getMouse().sendMouseEvent(btn, true); }
            catch (Exception ex) { LOGGER.fine("Mouse press error: " + ex.getMessage()); }
        });
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        MouseButton btn = awtButtonToKvm(e.getButton());
        if (btn == null) return;
        PiKvmClient c = client;
        if (c == null) return;
        inputSender.execute(() -> {
            try { c.getMouse().sendMouseEvent(btn, false); }
            catch (Exception ex) { LOGGER.fine("Mouse release error: " + ex.getMessage()); }
        });
    }

    @Override
    public void mouseClicked(MouseEvent e) { /* handled by pressed/released */ }

    @Override
    public void mouseEntered(MouseEvent e) { requestFocusInWindow(); }

    @Override
    public void mouseExited(MouseEvent e) { /* no-op */ }

    @Override
    public void mouseWheelMoved(MouseWheelEvent e) {
        int delta = -e.getWheelRotation();   // positive = up in PiKVM
        PiKvmClient c = client;
        if (c == null) return;
        inputSender.execute(() -> {
            try { c.getMouse().sendMouseWheelEvent(delta); }
            catch (Exception ex) { LOGGER.fine("Wheel error: " + ex.getMessage()); }
        });
    }

    // ── Keyboard events ───────────────────────────────────────────────────

    @Override
    public void keyPressed(KeyEvent e) {
        String domKey = AwtKeyMapper.toDomKey(e);
        if (domKey == null) return;
        PiKvmClient c = client;
        if (c == null) return;
        String event = c.getWsClient().createEvent(domKey, true);
        inputSender.execute(() -> {
            try { c.getWsClient().sendWithRetry(event); }
            catch (Exception ex) { LOGGER.fine("Key down error: " + ex.getMessage()); }
        });
        e.consume();
    }

    @Override
    public void keyReleased(KeyEvent e) {
        String domKey = AwtKeyMapper.toDomKey(e);
        if (domKey == null) return;
        PiKvmClient c = client;
        if (c == null) return;
        String event = c.getWsClient().createEvent(domKey, false);
        inputSender.execute(() -> {
            try { c.getWsClient().sendWithRetry(event); }
            catch (Exception ex) { LOGGER.fine("Key up error: " + ex.getMessage()); }
        });
        e.consume();
    }

    @Override
    public void keyTyped(KeyEvent e) { e.consume(); }   // handled at keyPressed/keyReleased

    // ── Cleanup ────────────────────────────────────────────────────────────

    /** Stops timers and shuts down the input sender. Call when closing the window. */
    void dispose() {
        repaintTimer.stop();
        mouseMoveTimer.stop();
        inputSender.shutdownNow();
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    /**
     * Converts panel pixel coordinates to remote screen coordinates,
     * accounting for letterbox / pillarbox offsets.
     *
     * @return {@code [remoteX, remoteY]}, or {@code null} if outside the image area
     */
    private int[] panelToRemote(int panelX, int panelY) {
        BufferedImage frame = latestFrame.get();
        if (frame == null) return null;
        DrawBounds db = computeDrawBounds(getWidth(), getHeight(), frame.getWidth(), frame.getHeight());
        // Clamp to image area
        int imgX = panelX - db.x;
        int imgY = panelY - db.y;
        if (imgX < 0 || imgX >= db.w || imgY < 0 || imgY >= db.h) {
            imgX = Math.max(0, Math.min(db.w - 1, imgX));
            imgY = Math.max(0, Math.min(db.h - 1, imgY));
        }
        // Scale back to remote resolution
        int rx = (int) ((double) imgX / db.w * remoteW);
        int ry = (int) ((double) imgY / db.h * remoteH);
        return new int[]{rx, ry};
    }

    private static DrawBounds computeDrawBounds(int pw, int ph, int fw, int fh) {
        double sx    = (double) pw / fw;
        double sy    = (double) ph / fh;
        double scale = Math.min(sx, sy);
        int w = (int) (fw * scale);
        int h = (int) (fh * scale);
        int x = (pw - w) / 2;
        int y = (ph - h) / 2;
        return new DrawBounds(x, y, w, h);
    }

    private static MouseButton awtButtonToKvm(int awtButton) {
        switch (awtButton) {
            case MouseEvent.BUTTON1: return MouseButton.LEFT;
            case MouseEvent.BUTTON2: return MouseButton.MIDDLE;
            case MouseEvent.BUTTON3: return MouseButton.RIGHT;
            default:                 return null;
        }
    }

    private record DrawBounds(int x, int y, int w, int h) {}
}
