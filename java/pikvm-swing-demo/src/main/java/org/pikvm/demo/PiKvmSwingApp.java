package org.pikvm.demo;

import org.pikvm.PiKvmClient;
import org.pikvm.atx.AtxButtonAction;
import org.pikvm.atx.AtxPowerAction;
import org.pikvm.desktop.PiKvmDesktop;
import org.pikvm.desktop.StreamerDesktop;
import org.pikvm.mouse.MouseButton;
import org.pikvm.msd.MsdParameters;
import org.pikvm.streamer.SnapshotResult;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * PiKVM Swing Demo Application.
 * <p>
 * Demonstrates all features of the pikvm-lib Java library through a tabbed GUI:
 * <ul>
 *   <li>Connection management</li>
 *   <li>System info &amp; metrics</li>
 *   <li>ATX power control</li>
 *   <li>GPIO channel control</li>
 *   <li>MSD (Mass Storage Device) management</li>
 *   <li>Streamer / snapshot capture</li>
 *   <li>Mouse control</li>
 *   <li>Keyboard control</li>
 * </ul>
 * </p>
 */
public class PiKvmSwingApp extends JFrame {

    // ── State ─────────────────────────────────────────────────────────────
    private PiKvmClient client;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "pikvm-worker");
        t.setDaemon(true);
        return t;
    });

    // ── Connection fields ─────────────────────────────────────────────────
    private JTextField hostField;
    private JTextField userField;
    private JPasswordField passField;
    private JComboBox<String> schemaBox;
    private JCheckBox trustTlsBox;
    private JButton connectBtn;
    private JButton disconnectBtn;
    private JLabel statusLabel;

    // ── Tabs ──────────────────────────────────────────────────────────────
    private JTabbedPane tabbedPane;

    // ── Log area ──────────────────────────────────────────────────────────
    private JTextArea logArea;

    // ── Snapshot display ─────────────────────────────────────────────────
    private ImagePanel snapshotPanel;

    // ── GPIO fields ───────────────────────────────────────────────────────
    private JTextField gpioChannelField;
    private JTextField gpioStateField;
    private JTextField gpioWaitField;
    private JTextField gpioPulseChannelField;
    private JTextField gpioPulseDelayField;
    private JTextField gpioPulseWaitField;

    // ── MSD fields ────────────────────────────────────────────────────────
    private JTextField msdImageNameField;
    private JTextField msdRemoteUrlField;
    private JTextField msdRemoteNameField;
    private JCheckBox  msdCdromBox;
    private JTextField msdParamNameField;
    private JTextField msdRemoveNameField;

    // ── Mouse fields ──────────────────────────────────────────────────────
    private JTextField mouseMoveXField;
    private JTextField mouseMoveYField;
    private JComboBox<MouseButton> mouseButtonBox;
    private JTextField mouseClickDelayField;
    private JTextField mouseWheelDeltaField;

    // ── Keyboard fields ───────────────────────────────────────────────────
    private JTextField keyPressField;
    private JTextField hotkeyField;
    private JTextField typeTextField;

    // ── System log fields ─────────────────────────────────────────────────
    private JTextField logSeekField;

    // ─────────────────────────────────────────────────────────────────────

    public PiKvmSwingApp() {
        super("PiKVM Swing Demo");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(900, 750);
        setLocationRelativeTo(null);
        buildUI();
        updateControlsEnabled(false);
    }

    // ── UI Construction ───────────────────────────────────────────────────

    private void buildUI() {
        JPanel root = new JPanel(new BorderLayout(4, 4));
        root.setBorder(new EmptyBorder(6, 6, 6, 6));
        setContentPane(root);

        root.add(buildConnectionPanel(), BorderLayout.NORTH);
        root.add(buildCenterPanel(),     BorderLayout.CENTER);
        root.add(buildLogPanel(),        BorderLayout.SOUTH);
    }

    private JPanel buildConnectionPanel() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        p.setBorder(new TitledBorder("Connection"));

        hostField   = new JTextField("192.168.1.10", 14);
        userField   = new JTextField("admin", 8);
        passField   = new JPasswordField("password", 8);
        schemaBox   = new JComboBox<>(new String[]{"https", "http"});
        trustTlsBox = new JCheckBox("Trust TLS", true);
        connectBtn    = new JButton("Connect");
        disconnectBtn = new JButton("Disconnect");
        statusLabel   = new JLabel("Not connected");
        statusLabel.setForeground(Color.RED);

        p.add(new JLabel("Host:"));     p.add(hostField);
        p.add(new JLabel("User:"));     p.add(userField);
        p.add(new JLabel("Pass:"));     p.add(passField);
        p.add(new JLabel("Schema:"));   p.add(schemaBox);
        p.add(trustTlsBox);
        p.add(connectBtn);
        p.add(disconnectBtn);
        p.add(statusLabel);

        JButton rdBtn = new JButton("🖥 Remote Desktop");
        rdBtn.setToolTipText("Open live 30+ FPS stream with full mouse & keyboard control");
        rdBtn.addActionListener(e -> {
            if (client == null) {
                log("Not connected – connect first.");
                return;
            }
            RemoteDesktopWindow.open(client, hostField.getText().trim());
        });

        connectBtn.addActionListener(e -> doConnect());
        disconnectBtn.addActionListener(e -> doDisconnect());

        p.add(rdBtn);

        return p;
    }

    private JSplitPane buildCenterPanel() {
        tabbedPane = new JTabbedPane();
        tabbedPane.addTab("System Info",  buildSystemInfoTab());
        tabbedPane.addTab("ATX",          buildAtxTab());
        tabbedPane.addTab("GPIO",         buildGpioTab());
        tabbedPane.addTab("MSD",          buildMsdTab());
        tabbedPane.addTab("Streamer",     buildStreamerTab());
        tabbedPane.addTab("Mouse",        buildMouseTab());
        tabbedPane.addTab("Keyboard",     buildKeyboardTab());

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, tabbedPane, buildLogPanel());
        split.setResizeWeight(0.65);
        split.setDividerSize(5);
        return split;
    }

    private JPanel buildLogPanel() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(new TitledBorder("Log"));
        logArea = new JTextArea(8, 80);
        logArea.setEditable(false);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane scroll = new JScrollPane(logArea);
        JButton clearBtn = new JButton("Clear");
        clearBtn.addActionListener(e -> logArea.setText(""));
        p.add(scroll,   BorderLayout.CENTER);
        p.add(clearBtn, BorderLayout.EAST);
        return p;
    }

    // ── Tabs ──────────────────────────────────────────────────────────────

    private JPanel buildSystemInfoTab() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));

        logSeekField = new JTextField("3600", 6);

        JButton infoBtn    = new JButton("Get System Info");
        JButton atxStateBtn= new JButton("Get ATX State");
        JButton promBtn    = new JButton("Prometheus Metrics");
        JButton authBtn    = new JButton("Check Auth");
        JButton logBtn     = new JButton("Fetch System Log");

        infoBtn.addActionListener(e ->
            runAsync("getSystemInfo", () -> log("System Info: " + client.getSystemInfo())));
        atxStateBtn.addActionListener(e ->
            runAsync("getAtxState", () -> log("ATX State: " + client.getAtxState())));
        promBtn.addActionListener(e ->
            runAsync("getPrometheusMetrics", () -> log("Metrics: " + client.getPrometheusMetrics())));
        authBtn.addActionListener(e ->
            runAsync("isAuth", () -> log("Auth: " + client.isAuth())));
        logBtn.addActionListener(e -> {
            int seek = parseIntField(logSeekField, 3600);
            runAsync("logSystemLog", () -> { client.logSystemLog(seek); log("System log fetched (see console)."); });
        });

        p.add(infoBtn);
        p.add(atxStateBtn);
        p.add(promBtn);
        p.add(authBtn);
        p.add(new JLabel("Log seek (s):"));
        p.add(logSeekField);
        p.add(logBtn);
        return p;
    }

    private JPanel buildAtxTab() {
        JPanel p = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 6, 4, 6);
        c.anchor = GridBagConstraints.WEST;

        // Power actions
        JPanel powerPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        powerPanel.setBorder(new TitledBorder("Power"));
        for (AtxPowerAction action : AtxPowerAction.values()) {
            JButton btn = new JButton(action.name());
            btn.addActionListener(e ->
                runAsync("setAtxPower:" + action, () -> {
                    client.setAtxPower(action);
                    log("ATX power set: " + action);
                }));
            powerPanel.add(btn);
        }

        // Button actions
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttonPanel.setBorder(new TitledBorder("Button Click"));
        for (AtxButtonAction action : AtxButtonAction.values()) {
            JButton btn = new JButton(action.name());
            btn.addActionListener(e ->
                runAsync("clickAtxButton:" + action, () -> {
                    client.clickAtxButton(action);
                    log("ATX button clicked: " + action);
                }));
            buttonPanel.add(btn);
        }

        c.gridx = 0; c.gridy = 0; c.fill = GridBagConstraints.HORIZONTAL; c.weightx = 1;
        p.add(powerPanel, c);
        c.gridy = 1;
        p.add(buttonPanel, c);
        return p;
    }

    private JPanel buildGpioTab() {
        JPanel p = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 6, 4, 6);
        c.anchor = GridBagConstraints.WEST;

        // State
        JButton stateBtn = new JButton("Get GPIO State");
        stateBtn.addActionListener(e ->
            runAsync("getGpioState", () -> log("GPIO State: " + client.getGpioState())));

        // Switch
        JPanel switchPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        switchPanel.setBorder(new TitledBorder("Switch Channel"));
        gpioChannelField = new JTextField("", 6);
        gpioStateField   = new JTextField("1", 4);
        gpioWaitField    = new JTextField("1", 4);
        JButton switchBtn = new JButton("Switch");
        switchBtn.addActionListener(e -> {
            String ch = gpioChannelField.getText().trim();
            int st  = parseIntField(gpioStateField, 1);
            int wt  = parseIntField(gpioWaitField, 1);
            runAsync("switchGpio", () -> {
                client.switchGpioChannel(ch, st, wt);
                log("GPIO switched: ch=" + ch + " state=" + st);
            });
        });
        switchPanel.add(new JLabel("Channel:")); switchPanel.add(gpioChannelField);
        switchPanel.add(new JLabel("State:"));   switchPanel.add(gpioStateField);
        switchPanel.add(new JLabel("Wait:"));    switchPanel.add(gpioWaitField);
        switchPanel.add(switchBtn);

        // Pulse
        JPanel pulsePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        pulsePanel.setBorder(new TitledBorder("Pulse Channel"));
        gpioPulseChannelField = new JTextField("", 6);
        gpioPulseDelayField   = new JTextField("0.0", 4);
        gpioPulseWaitField    = new JTextField("1", 4);
        JButton pulseBtn = new JButton("Pulse");
        pulseBtn.addActionListener(e -> {
            String ch  = gpioPulseChannelField.getText().trim();
            double dl  = parseDoubleField(gpioPulseDelayField, 0.0);
            int wt     = parseIntField(gpioPulseWaitField, 1);
            runAsync("pulseGpio", () -> {
                client.pulseGpioChannel(ch, dl, wt);
                log("GPIO pulsed: ch=" + ch + " delay=" + dl);
            });
        });
        pulsePanel.add(new JLabel("Channel:")); pulsePanel.add(gpioPulseChannelField);
        pulsePanel.add(new JLabel("Delay:"));   pulsePanel.add(gpioPulseDelayField);
        pulsePanel.add(new JLabel("Wait:"));    pulsePanel.add(gpioPulseWaitField);
        pulsePanel.add(pulseBtn);

        c.gridx = 0; c.gridy = 0; c.weightx = 1; c.fill = GridBagConstraints.NONE;
        p.add(stateBtn, c);
        c.gridy = 1; c.fill = GridBagConstraints.HORIZONTAL;
        p.add(switchPanel, c);
        c.gridy = 2;
        p.add(pulsePanel, c);
        return p;
    }

    private JPanel buildMsdTab() {
        JPanel p = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 6, 4, 6);
        c.anchor = GridBagConstraints.WEST;
        c.fill   = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;

        // State
        JButton stateBtn = new JButton("Get MSD State");
        stateBtn.addActionListener(e ->
            runAsync("getMsdState", () -> log("MSD State: " + client.getMsdState())));

        // Upload file
        JPanel uploadPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        uploadPanel.setBorder(new TitledBorder("Upload Image (file)"));
        msdImageNameField = new JTextField("", 12);
        JButton chooseFileBtn = new JButton("Choose & Upload");
        chooseFileBtn.addActionListener(e -> {
            JFileChooser fc = new JFileChooser();
            fc.setFileFilter(new FileNameExtensionFilter("ISO / IMG files", "iso", "img"));
            if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                File f = fc.getSelectedFile();
                String name = msdImageNameField.getText().trim();
                runAsync("uploadMsdImage", () -> {
                    if (name.isEmpty()) client.uploadMsdImage(f);
                    else                client.uploadMsdImage(f, name);
                    log("MSD image uploaded: " + f.getName());
                });
            }
        });
        uploadPanel.add(new JLabel("Image name (opt):"));
        uploadPanel.add(msdImageNameField);
        uploadPanel.add(chooseFileBtn);

        // Upload remote
        JPanel remotePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        remotePanel.setBorder(new TitledBorder("Upload Image (remote URL)"));
        msdRemoteUrlField  = new JTextField("", 20);
        msdRemoteNameField = new JTextField("", 12);
        JButton remoteBtn = new JButton("Upload Remote");
        remoteBtn.addActionListener(e -> {
            String url  = msdRemoteUrlField.getText().trim();
            String name = msdRemoteNameField.getText().trim();
            runAsync("uploadMsdRemote", () -> {
                if (name.isEmpty()) client.uploadMsdRemote(url);
                else                client.uploadMsdRemote(url, name);
                log("MSD remote upload started: " + url);
            });
        });
        remotePanel.add(new JLabel("URL:"));         remotePanel.add(msdRemoteUrlField);
        remotePanel.add(new JLabel("Name (opt):"));  remotePanel.add(msdRemoteNameField);
        remotePanel.add(remoteBtn);

        // Parameters
        JPanel paramPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        paramPanel.setBorder(new TitledBorder("Set Parameters"));
        msdParamNameField = new JTextField("", 12);
        msdCdromBox       = new JCheckBox("CDROM mode", false);
        JButton setParamBtn = new JButton("Set Params");
        setParamBtn.addActionListener(e -> {
            String name = msdParamNameField.getText().trim();
            boolean cdrom = msdCdromBox.isSelected();
            runAsync("setMsdParameters", () -> {
                client.setMsdParameters(MsdParameters.builder(name).cdrom(cdrom).build());
                log("MSD params set: name=" + name + " cdrom=" + cdrom);
            });
        });
        paramPanel.add(new JLabel("Image name:")); paramPanel.add(msdParamNameField);
        paramPanel.add(msdCdromBox);
        paramPanel.add(setParamBtn);

        // Remove
        JPanel removePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        removePanel.setBorder(new TitledBorder("Remove Image"));
        msdRemoveNameField = new JTextField("", 16);
        JButton removeBtn = new JButton("Remove");
        removeBtn.addActionListener(e -> {
            String name = msdRemoveNameField.getText().trim();
            runAsync("removeMsdImage", () -> {
                client.removeMsdImage(name);
                log("MSD image removed: " + name);
            });
        });
        removePanel.add(new JLabel("Image name:")); removePanel.add(msdRemoveNameField);
        removePanel.add(removeBtn);

        // Connect / Disconnect / Reset
        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        actionPanel.setBorder(new TitledBorder("MSD Actions"));
        JButton connBtn  = new JButton("Connect MSD");
        JButton discBtn  = new JButton("Disconnect MSD");
        JButton resetBtn = new JButton("Reset MSD");
        connBtn.addActionListener(e ->
            runAsync("connectMsd", () -> { client.connectMsd();    log("MSD connected."); }));
        discBtn.addActionListener(e ->
            runAsync("disconnectMsd", () -> { client.disconnectMsd(); log("MSD disconnected."); }));
        resetBtn.addActionListener(e ->
            runAsync("resetMsd", () -> { client.resetMsd(); log("MSD reset."); }));
        actionPanel.add(connBtn); actionPanel.add(discBtn); actionPanel.add(resetBtn);

        int row = 0;
        c.gridy = row++; p.add(stateBtn,   c);
        c.gridy = row++; p.add(uploadPanel, c);
        c.gridy = row++; p.add(remotePanel, c);
        c.gridy = row++; p.add(paramPanel,  c);
        c.gridy = row++; p.add(removePanel, c);
        c.gridy = row;   p.add(actionPanel, c);
        return p;
    }

    private JPanel buildStreamerTab() {
        JPanel p = new JPanel(new BorderLayout(6, 6));

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        controls.setBorder(new TitledBorder("Streamer Controls"));

        JButton stateBtn    = new JButton("Get Streamer State");
        JButton snapBtn     = new JButton("Capture Snapshot");
        JButton snapFileBtn = new JButton("Save Snapshot to File");

        stateBtn.addActionListener(e ->
            runAsync("getStreamerState", () -> log("Streamer State: " + client.getStreamerState())));

        snapBtn.addActionListener(e ->
            runAsync("getStreamerImage", () -> {
                SnapshotResult snap = client.getStreamerImage();
                log("Snapshot captured: " + snap.getImageBytes().length + " bytes");
                try {
                    BufferedImage img = StreamerDesktop.decodeImage(snap);
                    SwingUtilities.invokeLater(() -> snapshotPanel.setImage(img));
                } catch (Exception ex) {
                    log("ERROR decoding image: " + ex.getMessage());
                }
            }));

        snapFileBtn.addActionListener(e -> {
            JFileChooser fc = new JFileChooser();
            fc.setSelectedFile(new File("snapshot.jpg"));
            if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                File out = fc.getSelectedFile();
                runAsync("getStreamerSnapshot", () -> {
                    client.getStreamerSnapshot(out, false);
                    log("Snapshot saved to: " + out.getAbsolutePath());
                });
            }
        });

        controls.add(stateBtn);
        controls.add(snapBtn);
        controls.add(snapFileBtn);

        snapshotPanel = new ImagePanel();
        snapshotPanel.setBorder(new TitledBorder("Last Snapshot"));
        snapshotPanel.setPreferredSize(new Dimension(640, 360));

        p.add(controls,      BorderLayout.NORTH);
        p.add(new JScrollPane(snapshotPanel), BorderLayout.CENTER);
        return p;
    }

    private JPanel buildMouseTab() {
        JPanel p = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 6, 4, 6);
        c.anchor = GridBagConstraints.WEST;
        c.fill   = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;

        // Move
        JPanel movePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        movePanel.setBorder(new TitledBorder("Move Mouse"));
        mouseMoveXField = new JTextField("0", 6);
        mouseMoveYField = new JTextField("0", 6);
        JButton moveBtn = new JButton("Send Move");
        moveBtn.addActionListener(e -> {
            int x = parseIntField(mouseMoveXField, 0);
            int y = parseIntField(mouseMoveYField, 0);
            runAsync("sendMouseMoveEvent", () -> {
                client.sendMouseMoveEvent(x, y);
                log("Mouse moved to (" + x + "," + y + ")");
            });
        });
        movePanel.add(new JLabel("X:")); movePanel.add(mouseMoveXField);
        movePanel.add(new JLabel("Y:")); movePanel.add(mouseMoveYField);
        movePanel.add(moveBtn);

        // Click
        JPanel clickPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        clickPanel.setBorder(new TitledBorder("Click Mouse"));
        mouseButtonBox  = new JComboBox<>(new MouseButton[]{
            MouseButton.LEFT, MouseButton.RIGHT, MouseButton.MIDDLE});
        mouseClickDelayField = new JTextField("0", 6);
        JButton clickBtn = new JButton("Click");
        clickBtn.addActionListener(e -> {
            MouseButton btn = (MouseButton) mouseButtonBox.getSelectedItem();
            long delay = parseLongField(mouseClickDelayField, 0L);
            runAsync("sendClick", () -> {
                if (delay > 0) client.sendClick(btn, delay);
                else           client.sendClick(btn);
                log("Mouse click: " + btn + " delay=" + delay + "ms");
            });
        });
        clickPanel.add(new JLabel("Button:"));     clickPanel.add(mouseButtonBox);
        clickPanel.add(new JLabel("Delay (ms):")); clickPanel.add(mouseClickDelayField);
        clickPanel.add(clickBtn);

        // Wheel
        JPanel wheelPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        wheelPanel.setBorder(new TitledBorder("Mouse Wheel"));
        mouseWheelDeltaField = new JTextField("1", 6);
        JButton wheelBtn = new JButton("Send Wheel");
        wheelBtn.addActionListener(e -> {
            int delta = parseIntField(mouseWheelDeltaField, 1);
            runAsync("sendMouseWheelEvent", () -> {
                client.sendMouseWheelEvent(delta);
                log("Mouse wheel: delta=" + delta);
            });
        });
        wheelPanel.add(new JLabel("Delta:")); wheelPanel.add(mouseWheelDeltaField);
        wheelPanel.add(wheelBtn);

        // Press / Release
        JPanel pressPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        pressPanel.setBorder(new TitledBorder("Press / Release"));
        JComboBox<MouseButton> pressButtonBox = new JComboBox<>(new MouseButton[]{
            MouseButton.LEFT, MouseButton.RIGHT, MouseButton.MIDDLE});
        JButton pressBtn   = new JButton("Press");
        JButton releaseBtn = new JButton("Release");
        pressBtn.addActionListener(e -> {
            MouseButton btn = (MouseButton) pressButtonBox.getSelectedItem();
            runAsync("sendMouseEvent:press", () -> {
                client.sendMouseEvent(btn, true);
                log("Mouse press: " + btn);
            });
        });
        releaseBtn.addActionListener(e -> {
            MouseButton btn = (MouseButton) pressButtonBox.getSelectedItem();
            runAsync("sendMouseEvent:release", () -> {
                client.sendMouseEvent(btn, false);
                log("Mouse release: " + btn);
            });
        });
        pressPanel.add(new JLabel("Button:")); pressPanel.add(pressButtonBox);
        pressPanel.add(pressBtn); pressPanel.add(releaseBtn);

        int row = 0;
        c.gridy = row++; p.add(movePanel,  c);
        c.gridy = row++; p.add(clickPanel, c);
        c.gridy = row++; p.add(wheelPanel, c);
        c.gridy = row;   p.add(pressPanel, c);
        return p;
    }

    private JPanel buildKeyboardTab() {
        JPanel p = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets  = new Insets(4, 6, 4, 6);
        c.anchor  = GridBagConstraints.WEST;
        c.fill    = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;

        // Press key
        JPanel pressPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        pressPanel.setBorder(new TitledBorder("Press Key"));
        keyPressField = new JTextField("a", 10);
        JButton pressBtn = new JButton("Press");
        pressBtn.addActionListener(e -> {
            String key = keyPressField.getText().trim();
            runAsync("press", () -> { client.press(key); log("Key pressed: " + key); });
        });
        pressPanel.add(new JLabel("Key:")); pressPanel.add(keyPressField);
        pressPanel.add(pressBtn);

        // Hotkey
        JPanel hotkeyPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        hotkeyPanel.setBorder(new TitledBorder("Hotkey (comma-separated)"));
        hotkeyField = new JTextField("ctrl,alt,delete", 20);
        JButton hotkeyBtn = new JButton("Send Hotkey");
        hotkeyBtn.addActionListener(e -> {
            String[] keys = hotkeyField.getText().trim().split("\\s*,\\s*");
            runAsync("hotkey", () -> { client.hotkey(keys); log("Hotkey sent: " + hotkeyField.getText()); });
        });
        hotkeyPanel.add(new JLabel("Keys:")); hotkeyPanel.add(hotkeyField);
        hotkeyPanel.add(hotkeyBtn);

        // Type text
        JPanel typePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        typePanel.setBorder(new TitledBorder("Type Text"));
        typeTextField = new JTextField("Hello PiKVM!", 24);
        JButton typeBtn = new JButton("Type");
        typeBtn.addActionListener(e -> {
            String text = typeTextField.getText();
            runAsync("typeText", () -> { client.typeText(text); log("Typed: " + text); });
        });
        typePanel.add(new JLabel("Text:")); typePanel.add(typeTextField);
        typePanel.add(typeBtn);

        // keyDown / keyUp
        JPanel downUpPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        downUpPanel.setBorder(new TitledBorder("Key Down / Up"));
        JTextField keyDownField = new JTextField("shift", 10);
        JButton keyDownBtn = new JButton("Key Down");
        JButton keyUpBtn   = new JButton("Key Up");
        keyDownBtn.addActionListener(e -> {
            String key = keyDownField.getText().trim();
            runAsync("keyDown", () -> { client.keyDown(key); log("Key down: " + key); });
        });
        keyUpBtn.addActionListener(e -> {
            String key = keyDownField.getText().trim();
            runAsync("keyUp", () -> { client.keyUp(key); log("Key up: " + key); });
        });
        downUpPanel.add(new JLabel("Key:")); downUpPanel.add(keyDownField);
        downUpPanel.add(keyDownBtn); downUpPanel.add(keyUpBtn);

        int row = 0;
        c.gridy = row++; p.add(pressPanel,   c);
        c.gridy = row++; p.add(hotkeyPanel,  c);
        c.gridy = row++; p.add(typePanel,    c);
        c.gridy = row;   p.add(downUpPanel,  c);
        return p;
    }

    // ── Connection logic ──────────────────────────────────────────────────

    private void doConnect() {
        String host   = hostField.getText().trim();
        String user   = userField.getText().trim();
        String pass   = new String(passField.getPassword());
        String schema = (String) schemaBox.getSelectedItem();
        boolean trust = trustTlsBox.isSelected();

        connectBtn.setEnabled(false);
        statusLabel.setText("Connecting…");
        statusLabel.setForeground(Color.ORANGE);

        executor.submit(() -> {
            try {
                if (client != null) client.close();
                client = PiKvmDesktop.create(host, user, pass, schema, trust);
                boolean auth = client.isAuth();
                SwingUtilities.invokeLater(() -> {
                    if (auth) {
                        statusLabel.setText("Connected ✓");
                        statusLabel.setForeground(new Color(0, 150, 0));
                        updateControlsEnabled(true);
                        log("Connected to " + host + " as " + user);
                    } else {
                        statusLabel.setText("Auth failed");
                        statusLabel.setForeground(Color.RED);
                        connectBtn.setEnabled(true);
                        log("Authentication failed for " + user + "@" + host);
                    }
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    statusLabel.setText("Error: " + ex.getMessage());
                    statusLabel.setForeground(Color.RED);
                    connectBtn.setEnabled(true);
                    log("ERROR connecting: " + ex.getMessage());
                });
            }
        });
    }

    private void doDisconnect() {
        if (client != null) {
            try { client.close(); } catch (Exception ignored) {}
            client = null;
        }
        statusLabel.setText("Disconnected");
        statusLabel.setForeground(Color.RED);
        updateControlsEnabled(false);
        log("Disconnected.");
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private void runAsync(String name, Runnable task) {
        if (client == null) { log("Not connected."); return; }
        executor.submit(() -> {
            try {
                task.run();
            } catch (Exception ex) {
                log("ERROR [" + name + "]: " + ex.getMessage());
            }
        });
    }

    private void log(String msg) {
        String ts = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        String line = "[" + ts + "] " + msg + "\n";
        SwingUtilities.invokeLater(() -> {
            logArea.append(line);
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    private void updateControlsEnabled(boolean enabled) {
        tabbedPane.setEnabled(enabled);
        for (int i = 0; i < tabbedPane.getTabCount(); i++) {
            Component tab = tabbedPane.getComponentAt(i);
            setComponentsEnabled(tab, enabled);
        }
        disconnectBtn.setEnabled(enabled);
        connectBtn.setEnabled(!enabled);
    }

    private static void setComponentsEnabled(Component comp, boolean enabled) {
        if (comp instanceof Container) {
            for (Component child : ((Container) comp).getComponents()) {
                setComponentsEnabled(child, enabled);
            }
        }
        comp.setEnabled(enabled);
    }

    private static int parseIntField(JTextField f, int def) {
        try { return Integer.parseInt(f.getText().trim()); } catch (NumberFormatException e) { return def; }
    }

    private static long parseLongField(JTextField f, long def) {
        try { return Long.parseLong(f.getText().trim()); } catch (NumberFormatException e) { return def; }
    }

    private static double parseDoubleField(JTextField f, double def) {
        try { return Double.parseDouble(f.getText().trim()); } catch (NumberFormatException e) { return def; }
    }

    // ── Image panel ───────────────────────────────────────────────────────

    private static class ImagePanel extends JPanel {
        private BufferedImage image;

        void setImage(BufferedImage img) {
            this.image = img;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (image != null) {
                // Scale to fit preserving aspect ratio
                double sx = (double) getWidth()  / image.getWidth();
                double sy = (double) getHeight() / image.getHeight();
                double scale = Math.min(sx, sy);
                int w = (int) (image.getWidth()  * scale);
                int h = (int) (image.getHeight() * scale);
                int x = (getWidth()  - w) / 2;
                int y = (getHeight() - h) / 2;
                g.drawImage(image, x, y, w, h, this);
            } else {
                g.setColor(Color.DARK_GRAY);
                g.fillRect(0, 0, getWidth(), getHeight());
                g.setColor(Color.LIGHT_GRAY);
                g.drawString("No snapshot yet", getWidth() / 2 - 50, getHeight() / 2);
            }
        }
    }

    // ── Entry point ───────────────────────────────────────────────────────

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {}
            PiKvmSwingApp app = new PiKvmSwingApp();
            app.setVisible(true);
        });
    }
}
