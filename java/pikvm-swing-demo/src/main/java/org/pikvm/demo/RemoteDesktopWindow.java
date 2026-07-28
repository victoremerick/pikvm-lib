package org.pikvm.demo;

import org.pikvm.PiKvmClient;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Full-screen-capable remote desktop window for PiKVM.
 *
 * <p>Combines:</p>
 * <ul>
 *   <li>{@link RemoteDesktopPanel} – live MJPEG video at 30+ FPS with
 *       mouse and keyboard forwarding</li>
 *   <li>{@link MjpegStreamReceiver} – background MJPEG stream reader</li>
 *   <li>{@link AudioManager} – remote speaker playback and local mic capture</li>
 * </ul>
 *
 * <p>Open via {@link #open(PiKvmClient, String)} from any thread.</p>
 */
class RemoteDesktopWindow extends JFrame {

    private static final String TITLE = "PiKVM Remote Desktop";

    // ── Core components ────────────────────────────────────────────────────
    private final PiKvmClient        client;
    private final RemoteDesktopPanel videoPanel;
    private final MjpegStreamReceiver streamReceiver;
    private final AudioManager       audio;

    // ── Audio toolbar widgets ──────────────────────────────────────────────
    private JButton   speakerBtn;
    private JButton   micBtn;
    private JSlider   speakerSlider;
    private JSlider   micSlider;
    private JLabel    speakerStatusLabel;
    private JLabel    micStatusLabel;
    private LevelMeter speakerMeter;
    private LevelMeter micMeter;

    // ── FPS / status ───────────────────────────────────────────────────────
    private JLabel fpsLabel;
    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "pikvm-rd-scheduler");
                t.setDaemon(true);
                return t;
            });

    // ── Factory ────────────────────────────────────────────────────────────

    /**
     * Creates and shows a remote desktop window for the given connected client.
     *
     * @param client     authenticated PiKVM client
     * @param hostname   displayed in the title bar
     */
    static void open(PiKvmClient client, String hostname) {
        SwingUtilities.invokeLater(() -> {
            RemoteDesktopWindow w = new RemoteDesktopWindow(client, hostname);
            w.setVisible(true);
            w.startStreaming();
        });
    }

    // ── Constructor ────────────────────────────────────────────────────────

    private RemoteDesktopWindow(PiKvmClient client, String hostname) {
        super(TITLE + "  –  " + hostname);
        this.client         = client;
        this.videoPanel     = new RemoteDesktopPanel();
        this.streamReceiver = new MjpegStreamReceiver();
        this.audio          = new AudioManager(client.getHttpClient(), this::onAudioStatus);

        videoPanel.setClient(client);

        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { shutdown(); }
        });

        buildUI();
        setSize(1024, 700);
        setLocationRelativeTo(null);
    }

    // ── UI construction ────────────────────────────────────────────────────

    private void buildUI() {
        JPanel root = new JPanel(new BorderLayout(2, 2));
        setContentPane(root);

        // Video fills the center
        root.add(videoPanel, BorderLayout.CENTER);

        // Bottom toolbar: status + audio controls
        root.add(buildBottomBar(), BorderLayout.SOUTH);
    }

    private JPanel buildBottomBar() {
        JPanel bar = new JPanel(new BorderLayout(8, 0));
        bar.setBorder(new EmptyBorder(3, 6, 3, 6));
        bar.setBackground(new Color(40, 40, 40));

        bar.add(buildStatusPanel(), BorderLayout.WEST);
        bar.add(buildAudioPanel(),  BorderLayout.CENTER);
        bar.add(buildHintsLabel(),  BorderLayout.EAST);

        return bar;
    }

    private JPanel buildStatusPanel() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        p.setOpaque(false);

        fpsLabel = darkLabel("FPS: --.-");
        fpsLabel.setForeground(Color.GREEN);
        fpsLabel.setFont(new Font(Font.MONOSPACED, Font.BOLD, 13));

        JButton fullscreenBtn = darkButton("⛶ Fullscreen");
        fullscreenBtn.addActionListener(e -> toggleFullscreen());

        p.add(fpsLabel);
        p.add(Box.createHorizontalStrut(10));
        p.add(fullscreenBtn);
        return p;
    }

    private JPanel buildAudioPanel() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        p.setOpaque(false);

        // ── Speaker ──────────────────────────────────────────────────────
        speakerBtn    = darkButton("🔊 Speaker: OFF");
        speakerSlider = new JSlider(0, 100, 80);
        speakerSlider.setPreferredSize(new Dimension(80, 20));
        speakerSlider.setOpaque(false);
        speakerSlider.setToolTipText("Speaker volume");
        speakerMeter        = new LevelMeter(Color.GREEN);
        speakerStatusLabel  = darkLabel("");
        speakerStatusLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
        speakerStatusLabel.setForeground(Color.LIGHT_GRAY);

        speakerBtn.addActionListener(e -> toggleSpeaker());
        speakerSlider.addChangeListener(e -> audio.setSpeakerVolume(speakerSlider.getValue()));

        // ── Microphone ────────────────────────────────────────────────────
        micBtn    = darkButton("🎤 Mic: OFF");
        micSlider = new JSlider(0, 100, 80);
        micSlider.setPreferredSize(new Dimension(80, 20));
        micSlider.setOpaque(false);
        micSlider.setToolTipText("Microphone gain");
        micMeter        = new LevelMeter(Color.ORANGE);
        micStatusLabel  = darkLabel("");
        micStatusLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
        micStatusLabel.setForeground(Color.LIGHT_GRAY);

        micBtn.addActionListener(e -> toggleMic());
        micSlider.addChangeListener(e -> audio.setMicVolume(micSlider.getValue()));

        p.add(darkLabel("Speaker:"));
        p.add(speakerBtn);
        p.add(speakerSlider);
        p.add(speakerMeter);
        p.add(speakerStatusLabel);
        p.add(Box.createHorizontalStrut(12));
        p.add(darkLabel("Mic:"));
        p.add(micBtn);
        p.add(micSlider);
        p.add(micMeter);
        p.add(micStatusLabel);
        return p;
    }

    private JLabel buildHintsLabel() {
        JLabel l = darkLabel("<html><font color='#999999'>"
                + "Click panel → focus &nbsp;|&nbsp; ESC+F = fullscreen"
                + "</font></html>");
        l.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
        return l;
    }

    // ── Streaming lifecycle ────────────────────────────────────────────────

    private void startStreaming() {
        streamReceiver.start(client.getHttpClient(), (jpeg, w, h) -> {
            videoPanel.onFrame(jpeg, w, h);
        });

        // Update FPS label and level meters at ~4 Hz
        scheduler.scheduleAtFixedRate(() -> SwingUtilities.invokeLater(this::updateStatus),
                250, 250, TimeUnit.MILLISECONDS);
    }

    private void updateStatus() {
        double fps = streamReceiver.getCurrentFps();
        videoPanel.setStreamFps(fps, streamReceiver.isRunning());
        fpsLabel.setText(String.format("FPS: %4.1f", fps));
        fpsLabel.setForeground(fps >= 25 ? Color.GREEN : (fps > 10 ? Color.YELLOW : Color.RED));
        speakerMeter.setLevel(audio.speakerLevel);
        micMeter.setLevel(audio.micLevel);
    }

    private void shutdown() {
        streamReceiver.stop();
        audio.close();
        scheduler.shutdownNow();
        videoPanel.dispose();
        dispose();
    }

    // ── Audio control ──────────────────────────────────────────────────────

    private void toggleSpeaker() {
        if (audio.isSpeakerRunning()) {
            audio.stopSpeaker();
            speakerBtn.setText("🔊 Speaker: OFF");
        } else {
            audio.startSpeaker();
            speakerBtn.setText("🔇 Speaker: ON");
        }
    }

    private void toggleMic() {
        if (audio.isMicRunning()) {
            audio.stopMic();
            micBtn.setText("🎤 Mic: OFF");
        } else {
            audio.startMic();
            micBtn.setText("🔴 Mic: ON");
        }
    }

    private void onAudioStatus(String kind, String message, boolean ok) {
        SwingUtilities.invokeLater(() -> {
            if ("speaker".equals(kind)) {
                speakerStatusLabel.setText(message);
                speakerStatusLabel.setForeground(ok ? Color.GREEN : Color.ORANGE);
                if (!ok && !audio.isSpeakerRunning()) speakerBtn.setText("🔊 Speaker: OFF");
            } else {
                micStatusLabel.setText(message);
                micStatusLabel.setForeground(ok ? Color.GREEN : Color.ORANGE);
                if (!ok && !audio.isMicRunning()) micBtn.setText("🎤 Mic: OFF");
            }
        });
    }

    // ── Fullscreen toggle ──────────────────────────────────────────────────

    private boolean isFullscreen = false;

    private void toggleFullscreen() {
        GraphicsDevice gd = GraphicsEnvironment
                .getLocalGraphicsEnvironment()
                .getDefaultScreenDevice();
        if (!isFullscreen && gd.isFullScreenSupported()) {
            dispose();
            setUndecorated(true);
            gd.setFullScreenWindow(this);
            isFullscreen = true;
        } else {
            gd.setFullScreenWindow(null);
            dispose();
            setUndecorated(false);
            pack();
            setSize(1024, 700);
            setVisible(true);
            isFullscreen = false;
        }
    }

    // ── UI helpers ─────────────────────────────────────────────────────────

    private static JLabel darkLabel(String text) {
        JLabel l = new JLabel(text);
        l.setForeground(Color.LIGHT_GRAY);
        return l;
    }

    private static JButton darkButton(String text) {
        JButton b = new JButton(text);
        b.setBackground(new Color(60, 60, 60));
        b.setForeground(Color.WHITE);
        b.setFocusable(false);
        b.setBorderPainted(false);
        b.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        return b;
    }

    // ── Level meter ────────────────────────────────────────────────────────

    /**
     * A compact horizontal bar showing an audio level.
     */
    private static final class LevelMeter extends JComponent {
        private final Color barColor;
        private float level = 0f;

        LevelMeter(Color color) {
            this.barColor = color;
            setPreferredSize(new Dimension(50, 10));
            setOpaque(false);
        }

        void setLevel(float l) {
            this.level = l;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            int w = getWidth(), h = getHeight();
            g.setColor(new Color(30, 30, 30));
            g.fillRect(0, 0, w, h);
            int barW = (int) (Math.min(1f, level) * w);
            if (barW > 0) {
                g.setColor(barColor);
                g.fillRect(0, 0, barW, h);
            }
        }
    }
}
