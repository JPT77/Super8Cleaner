package de.jpt.super8;

import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseWheelListener;
import java.io.File;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JToolBar;
import javax.swing.KeyStroke;
import javax.swing.Timer;
import javax.swing.filechooser.FileNameExtensionFilter;

import net.miginfocom.swing.MigLayout;

/**
 * Hauptfenster des Super-8-Players (reine Swing-GUI, kein direkter OpenCV-Zugriff).
 * Kommuniziert ausschliesslich ueber {@link VideoController}.
 */
public class VideoPlayer extends JFrame {

    private static final long serialVersionUID = 1L;

    private final VideoController controller = new VideoController();

    private final VideoPanel originalPanel = new VideoPanel();
    private final VideoPanel processedPanel = new VideoPanel();
    private final JScrollPane leftScroll = new JScrollPane(originalPanel);
    private final JScrollPane rightScroll = new JScrollPane(processedPanel);
    private final StatusBar statusBar = new StatusBar();

    private final JButton btnOpen = new JButton("Open");
    private final JButton btnPlay = new JButton("Play");
    private final JButton btnStop = new JButton("Stop");
    private final JButton btnPrev = new JButton("<");
    private final JButton btnNext = new JButton(">");
    private final JCheckBox chkCanny = new JCheckBox("Canny", true);
    private final JSlider slider = new JSlider(0, 0, 0);

    private Timer playTimer;
    private boolean updatingSlider = false;

    public VideoPlayer() {
        super("Super8");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        buildGui();
        installEvents();
        installKeyBindings();
        pack();
        setLocationRelativeTo(null);
    }

    // ------------------------------------------------------------------ GUI

    private void buildGui() {
        JPanel root = new JPanel(new MigLayout(
                "fill,insets 5",
                "[grow][grow]",
                "[][grow][]10[]"));

        JToolBar tb = new JToolBar();
        tb.setFloatable(false);
        tb.add(btnOpen);
        tb.addSeparator();
        tb.add(btnPlay);
        tb.add(btnStop);
        tb.addSeparator();
        tb.add(btnPrev);
        tb.add(btnNext);
        tb.addSeparator();
        tb.add(chkCanny);
        root.add(tb, "span 2,growx,wrap");

        root.add(leftScroll, "grow,push");
        root.add(rightScroll, "grow,push,wrap");
        root.add(slider, "span 2,growx,wrap");
        root.add(statusBar, "span 2,growx");

        setContentPane(root);
        setPlaybackEnabled(false);
    }

    // --------------------------------------------------------------- Events

    private void installEvents() {
        btnOpen.addActionListener(e -> openVideo());
        btnPlay.addActionListener(e -> togglePlay());
        btnStop.addActionListener(e -> stop());
        btnPrev.addActionListener(e -> step(-1));
        btnNext.addActionListener(e -> step(1));
        chkCanny.addActionListener(e -> refresh());

        slider.addChangeListener(e -> {
            if (updatingSlider) {
                return;
            }
            showFrame(slider.getValue());
        });

        MouseWheelListener wheel = e -> {
            if (!controller.isOpen()) {
                return;
            }
            int mult = 1;
            if (e.isControlDown()) {
                mult = 10;
            }
            if (e.isShiftDown()) {
                mult = 100;
            }
            step(e.getWheelRotation() * mult);
        };
        originalPanel.addMouseWheelListener(wheel);
        processedPanel.addMouseWheelListener(wheel);
    }

    private void installKeyBindings() {
        JComponent c = getRootPane();
        InputMap im = c.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = c.getActionMap();

        bind(im, am, "prev1", KeyEvent.VK_LEFT, 0, () -> step(-1));
        bind(im, am, "next1", KeyEvent.VK_RIGHT, 0, () -> step(1));
        bind(im, am, "prev10", KeyEvent.VK_LEFT, InputEvent.CTRL_DOWN_MASK, () -> step(-10));
        bind(im, am, "next10", KeyEvent.VK_RIGHT, InputEvent.CTRL_DOWN_MASK, () -> step(10));
        bind(im, am, "prev100", KeyEvent.VK_LEFT, InputEvent.SHIFT_DOWN_MASK, () -> step(-100));
        bind(im, am, "next100", KeyEvent.VK_RIGHT, InputEvent.SHIFT_DOWN_MASK, () -> step(100));
        bind(im, am, "first", KeyEvent.VK_HOME, 0, () -> showFrame(0));
        bind(im, am, "last", KeyEvent.VK_END, 0, () -> showFrame(controller.getInfo().totalFrames - 1));
        bind(im, am, "play", KeyEvent.VK_SPACE, 0, this::togglePlay);
    }

    private void bind(InputMap im, ActionMap am, String key, int code, int mod, Runnable r) {
        im.put(KeyStroke.getKeyStroke(code, mod), key);
        am.put(key, new AbstractAction() {
            private static final long serialVersionUID = 1L;
            @Override
            public void actionPerformed(ActionEvent e) {
                if (controller.isOpen()) {
                    r.run();
                }
            }
        });
    }

    // ------------------------------------------------------------- Aktionen

    private void openVideo() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Video auswählen");
        chooser.setFileFilter(new FileNameExtensionFilter(
                "Videos", "mp4", "avi", "mov", "mkv", "m4v", "mpg", "mpeg"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File file = chooser.getSelectedFile();
        if (!controller.open(file)) {
            JOptionPane.showMessageDialog(this, "Video konnte nicht geöffnet werden.");
            return;
        }
        stop();
        VideoInfo info = controller.getInfo();
        updatingSlider = true;
        slider.setMinimum(0);
        slider.setMaximum(Math.max(0, info.totalFrames - 1));
        slider.setValue(0);
        updatingSlider = false;
        setPlaybackEnabled(true);
        sizeToVideo(info);
        showFrame(0);
    }

    /** Fenstergroesse an die Originalaufloesung des Videos anpassen. */
    private void sizeToVideo(VideoInfo info) {
        originalPanel.setFitToWindow(false);
        processedPanel.setFitToWindow(false);
        Dimension d = new Dimension(info.width, info.height);
        originalPanel.setPreferredSize(d);
        processedPanel.setPreferredSize(d);
        pack();
        Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        setSize(Math.min(getWidth(), screen.width), Math.min(getHeight(), screen.height));
        setLocationRelativeTo(null);
    }

    private void showFrame(int frame) {
        if (!controller.isOpen()) {
            return;
        }
        VideoInfo info = controller.getInfo();
        frame = Math.max(0, Math.min(frame, info.totalFrames - 1));
        if (!controller.seek(frame)) {
            return;
        }
        originalPanel.setImage(controller.getOriginalImage());
        processedPanel.setImage(controller.getProcessedImage(chkCanny.isSelected()));
        updatingSlider = true;
        slider.setValue(frame);
        updatingSlider = false;
        statusBar.update(info);
    }

    private void refresh() {
        if (controller.isOpen()) {
            showFrame(controller.getInfo().currentFrame);
        }
    }

    private void step(int delta) {
        if (controller.isOpen()) {
            showFrame(controller.getInfo().currentFrame + delta);
        }
    }

    // -------------------------------------------------------------- Wiedergabe

    private void togglePlay() {
        if (!controller.isOpen()) {
            return;
        }
        if (playTimer != null && playTimer.isRunning()) {
            stop();
        } else {
            play();
        }
    }

    private void play() {
        VideoInfo info = controller.getInfo();
        int delay = info.fps > 0 ? (int) Math.round(1000.0 / info.fps) : 40;
        playTimer = new Timer(delay, e -> {
            int next = controller.getInfo().currentFrame + 1;
            if (next >= controller.getInfo().totalFrames) {
                stop();
                return;
            }
            showFrame(next);
        });
        playTimer.start();
        btnPlay.setText("Pause");
    }

    private void stop() {
        if (playTimer != null) {
            playTimer.stop();
        }
        btnPlay.setText("Play");
    }

    private void setPlaybackEnabled(boolean on) {
        btnPlay.setEnabled(on);
        btnStop.setEnabled(on);
        btnPrev.setEnabled(on);
        btnNext.setEnabled(on);
        chkCanny.setEnabled(on);
        slider.setEnabled(on);
    }
}
