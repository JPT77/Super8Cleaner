package de.jpt.super8;

import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseWheelListener;
import java.awt.image.BufferedImage;
import java.io.File;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.DefaultListModel;
import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JTabbedPane;
import javax.swing.JToolBar;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.filechooser.FileNameExtensionFilter;

import org.bytedeco.javacpp.indexer.UByteIndexer;
import org.bytedeco.opencv.opencv_core.Mat;

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
    private final StatusBar statusBar = new StatusBar();

    private final JButton btnOpen = new JButton("Open");
    private final JButton btnPlay = new JButton("Play");
    private final JButton btnStop = new JButton("Stop");
    private final JButton btnPrev = new JButton("<");
    private final JButton btnNext = new JButton(">");
    private final JSlider slider = new JSlider(0, 0, 0);

    private final BrightnessGraphPanel verticalProfilePanel = new BrightnessGraphPanel(BrightnessGraphPanel.Orientation.VERTICAL);
    private final BrightnessGraphPanel horizontalProfilePanel = new BrightnessGraphPanel(BrightnessGraphPanel.Orientation.HORIZONTAL);

	private final JPanel filterButtonPanel = new JPanel(new MigLayout("fillx,wrap"));

	private final JButton btnAddFilter = new JButton("Add");
	private final JButton btnDeleteFilter = new JButton("Delete");
	private final JButton btnConfigFilter = new JButton("Config");
	private final JButton btnFilterUp = new JButton("↑");
	private final JButton btnFilterDown = new JButton("↓");

	private final DefaultListModel<AbstractFilter> filterModel = new DefaultListModel<AbstractFilter>();
	private final JList<AbstractFilter> filterList = new JList<AbstractFilter>(filterModel);
	private final JScrollPane filterScroll = new JScrollPane(filterList);
	
	private final JTabbedPane sideTabs = new JTabbedPane();

	// Szenen-Tab
	private final DefaultListModel<Scene> sceneModel = new DefaultListModel<Scene>();
	private final JList<Scene> sceneList = new JList<>(sceneModel);
	private final JScrollPane sceneScroll = new JScrollPane(sceneList);

	private final JPanel sceneButtonPanel = new JPanel(new MigLayout("fillx,wrap 2","[grow][grow]"));

	private final JButton btnSceneStart    = new JButton("Scene Start");
	private final JButton btnSceneEnd      = new JButton("Scene End");
	private final JButton btnAddScene      = new JButton("Add Scene");
	private final JButton btnFindNextScene = new JButton("Find Next Scene");

	// Zwischenspeicher beim Definieren einer neuen Szene
	private int pendingSceneStart = -1;
	private int pendingSceneEnd   = -1;

	private SceneDetectionWindow sceneWindow;
	private volatile boolean searching = false;

	private final MigLayout rootLayout = new MigLayout(
			"fill,insets 5",
			"[grow][90!][grow]",
			"[]["+"pref"+"!][grow][]10[]");
	private JPanel root = new JPanel(rootLayout);

	/** Analyse-Fenster (wird beim Oeffnen eines neuen Videos erzeugt). */
	private FrameAnalysisWindow frameAnalysisWindow;

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
        filterList.setCellRenderer(new FilterRenderer());
        filterList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    }

    // ------------------------------------------------------------------ GUI

    private void buildGui() {

    	// --- Toolbar
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
    	root.add(tb, "span 3,growx,wrap");

    	// --- obere Reihe

    	root.add(originalPanel, "shrink");
    	verticalProfilePanel.setBorder(javax.swing.BorderFactory.createTitledBorder("Vertical"));
    	root.add(verticalProfilePanel, "growy,growx");
    	root.add(processedPanel, "grow,push,wrap");

    	// --- untere Reihe
    	horizontalProfilePanel.setBorder(javax.swing.BorderFactory.createTitledBorder("Horizontal"));
    	root.add(horizontalProfilePanel, "grow");

    	// --- Filter-Tab ---
    	JPanel filterTab = new JPanel(new MigLayout("fill,insets 3", "[fill]3[grow,fill]", "[grow]"));
//		"fill,insets 3","[grow]","[][grow]"));
    	filterButtonPanel.add(btnAddFilter,    "growx, wrap");
    	filterButtonPanel.add(btnDeleteFilter, "growx, wrap");
    	filterButtonPanel.add(btnConfigFilter, "growx, wrap");
    	filterButtonPanel.add(btnFilterUp,     "split 2,growx, wrap");
    	filterButtonPanel.add(btnFilterDown,   "growx");
    	filterTab.add(filterButtonPanel, "growy");
    	filterTab.add(filterScroll,      "grow,push");

    	// --- Szenen-Tab ---
    	JPanel sceneTab = new JPanel(new MigLayout("fill,insets 2", "[fill]2[grow,fill]", "[grow]"));
    	sceneButtonPanel.add(btnSceneStart,    "growx, wrap");
    	sceneButtonPanel.add(btnSceneEnd,      "growx, wrap");
    	sceneButtonPanel.add(btnAddScene,      "growx, wrap");
    	sceneButtonPanel.add(btnFindNextScene, "growx");
    	sceneTab.add(sceneButtonPanel, "growy");
    	sceneTab.add(sceneScroll, "grow, push");

    	sideTabs.addTab("Filters", filterTab);
    	sideTabs.addTab("Scenes",  sceneTab);

    	// im root statt der zwei alten Zellen jetzt nur noch eine Zelle:
    	root.add(sideTabs, "span 2,grow,wrap");
//    	root.add(sideTabs, "span 2,grow");

    	// ------------------------------------------------ Slider + Status

    	root.add(slider, "span 3,growx,wrap");
    	root.add(statusBar, "span 3,growx");

    	root.addComponentListener(new java.awt.event.ComponentAdapter() {
    		@Override public void componentResized(java.awt.event.ComponentEvent e) {
    			updateVideoRowHeight();
    		}
    	});

    	setContentPane(root);
    	setPlaybackEnabled(false);
    }

    private void updateVideoRowHeight() {
    	if (!controller.isOpen() || root == null) return;
    	VideoInfo info = controller.getInfo();
    	if (info.width <= 0 || info.height <= 0) return;

    	double aspect = (double) info.width / info.height;

    	// verfügbare Breite pro Videopanel:
    	// Gesamtbreite - insets(2*5) - mittlere Spalte(90) - ca. 3 gaps à 7px
    	int available = root.getWidth() - 10 - 90 - 21;
    	if (available <= 0) return;
    	int videoW = available / 2;
    	int videoH = (int) Math.round(videoW / aspect);

    	rootLayout.setRowConstraints("["+"]["+videoH+"!][grow][]10[]");
    	root.revalidate();
    }

    // --------------------------------------------------------------- Events

    private void installEvents() {
        btnOpen.addActionListener(e -> openVideo());
        btnPlay.addActionListener(e -> togglePlay());
        btnStop.addActionListener(e -> stop());
        btnPrev.addActionListener(e -> step(-1));
        btnNext.addActionListener(e -> step(1));

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
        
        btnAddFilter.addActionListener(e -> {

            String name = JOptionPane.showInputDialog(
                    this,
                    "Filtername");

            if (name != null && !name.isBlank()) {
//             TODO   filterModel.addElement(new AbstractFilter(name));
            }
        });
        
        btnDeleteFilter.addActionListener(e -> {

            int idx = filterList.getSelectedIndex();

            if (idx >= 0)
                filterModel.remove(idx);
        });

        btnFilterUp.addActionListener(e -> {
            int i = filterList.getSelectedIndex();
            if (i > 0) {
                AbstractFilter f = filterModel.remove(i);
                filterModel.add(i - 1, f);
                filterList.setSelectedIndex(i - 1);
            }
        });

        btnFilterDown.addActionListener(e -> {
            int i = filterList.getSelectedIndex();
            if (i >= 0 && i < filterModel.size() - 1) {
                AbstractFilter f = filterModel.remove(i);
                filterModel.add(i + 1, f);
                filterList.setSelectedIndex(i + 1);
            }
        });

        btnSceneStart.addActionListener(e -> {
            if (controller.isOpen())
                pendingSceneStart = controller.getInfo().currentFrame;
        });

        btnSceneEnd.addActionListener(e -> {
            if (controller.isOpen())
                pendingSceneEnd = controller.getInfo().currentFrame;
        });

        btnAddScene.addActionListener(e -> {
            if (pendingSceneStart < 0 || pendingSceneEnd < 0 || pendingSceneEnd < pendingSceneStart) {
                JOptionPane.showMessageDialog(this,
                    "Bitte zuerst gültigen Scene Start und Scene End setzen.");
                return;
            }
            String name = JOptionPane.showInputDialog(this,
                "Szenenname", "Scene " + (sceneModel.size() + 1));
            if (name == null || name.isBlank()) return;
            sceneModel.addElement(new Scene(name, pendingSceneStart, pendingSceneEnd));
            pendingSceneStart = -1;
            pendingSceneEnd   = -1;
        });

        btnFindNextScene.addActionListener(e -> findNextScene());

        // Doppelklick auf eine Szene → dorthin springen
        sceneList.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent evt) {
                if (evt.getClickCount() == 2) {
                    Scene s = sceneList.getSelectedValue();
                    if (s != null) showFrame(s.getStart());
                }
            }
        });
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

        // Overlay des alten Videos leeren, dann Analyse-Fenster fuer neues Video oeffnen
        originalPanel.setFrameOverlay(null, Double.NaN);
        if (frameAnalysisWindow != null && frameAnalysisWindow.isDisplayable()) {
            frameAnalysisWindow.dispose();
        }
        frameAnalysisWindow = new FrameAnalysisWindow(file, info.totalFrames, info.fps);
        frameAnalysisWindow.setVisible(true);

        showFrame(0);
    }

    private void sizeToVideo(VideoInfo info) {

        Rectangle screen =
                GraphicsEnvironment.getLocalGraphicsEnvironment()
                                   .getMaximumWindowBounds();

        // etwas Platz für Toolbar, Statusleiste usw.
        int maxW = screen.width  - 80;
        int maxH = screen.height - 180;

        double scale = Math.min(
                1.0,
                Math.min((double) maxW / info.width,
                         (double) maxH / info.height));

        int w = (int)Math.round(info.width  * scale);
        int h = (int)Math.round(info.height * scale);

        originalPanel.setPreferredSize(new Dimension(w, h));
        processedPanel.setPreferredSize(new Dimension(w, h));

        updateVideoRowHeight();
        pack();
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
        processedPanel.setImage(controller.getProcessedImage(filterList));

        // Analyse-Overlay (falls Lauf 1 fuer diesen Frame bereits vorliegt)
        if (frameAnalysisWindow != null) {
            FrameInfo fi = frameAnalysisWindow.getFrameInfo(frame);
            originalPanel.setFrameOverlay(fi, frameAnalysisWindow.getAvgMinDistance());
        } else {
            originalPanel.setFrameOverlay(null, Double.NaN);
        }

        updatingSlider = true;
        slider.setValue(frame);
        updatingSlider = false;
        statusBar.update(info);

        Mat mat = originalPanel.getImageAsMat();
        UByteIndexer idx = mat.createIndexer();
        try {
            verticalProfilePanel.setValues(BrightnessProfiles.horizontalProfile(mat, idx));
            horizontalProfilePanel.setValues(BrightnessProfiles.verticalProfile(mat, idx));
        }
        finally {
            idx.release();
        }
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
        slider.setEnabled(on);
    }

    private void findNextScene() {

        if (!controller.isOpen())
            return;


        if (sceneWindow == null ||
            !sceneWindow.isDisplayable()) {


            sceneWindow =
                    new SceneDetectionWindow(
                            Config.SCENE_THRESHOLD);


            sceneWindow.setSearchAction(
                    this::startSceneSearch);


            sceneWindow.setVisible(true);

        } else {

            sceneWindow.setVisible(true);
            sceneWindow.toFront();
            sceneWindow.requestFocus();
        }
    }

    private BrightnessInfo analyzeBrightness(BufferedImage img) {

        int width = img.getWidth();
        int height = img.getHeight();

        long pixels = (long) width * height;

        double sum = 0;
        double sumSquared = 0;


        for (int y = 0; y < height; y++) {

            for (int x = 0; x < width; x++) {

                int rgb = img.getRGB(x, y);

                int r = (rgb >> 16) & 0xff;
                int g = (rgb >> 8) & 0xff;
                int b = rgb & 0xff;


                // Helligkeit wie vorher:
                double gray =
                        (r + g + b) / 3.0;


                sum += gray;
                sumSquared += gray * gray;
            }
        }


        double mean =
                sum / pixels;


        double variance =
                (sumSquared / pixels)
                - (mean * mean);


        double stdDev =
                Math.sqrt(
                        Math.max(0, variance));


        return new BrightnessInfo(
                mean,
                stdDev);
    }

    private void startSceneSearch() {

        if (searching)
            return;

        searching = true;


        Thread searchThread = new Thread(() -> {

            VideoInfo info = controller.getInfo();

            int startFrame = info.currentFrame + 1;


            BrightnessInfo previous =
                    analyzeBrightness(
                            controller.getOriginalImage());


            for (int frame = startFrame;
                 frame < info.totalFrames && searching;
                 frame++) {

                if (!controller.nextFrame())
                    break;


                BrightnessInfo current =
                        analyzeBrightness(
                                controller.getOriginalImage());


                double difference =
                        Math.abs(
                                current.mean -
                                previous.mean);



                final int displayFrame = frame;
                final double displayCurrent =
                        current.mean;
                final double displayPrevious =
                        previous.mean;
                final double displayStd =
                        current.stdDev;
                final double displayDifference =
                        difference;


                SwingUtilities.invokeLater(() -> {

                    if (sceneWindow != null) {

                        sceneWindow.updateValues(
                                displayFrame,
                                displayCurrent,
                                displayPrevious,
                                displayStd,
                                displayDifference,
                                Config.SCENE_THRESHOLD);
                    }
                });



                if (difference >= Config.SCENE_THRESHOLD) {


                    final int foundFrame = frame;


                    SwingUtilities.invokeLater(() -> {

                        showFrame(foundFrame);


                        if (sceneWindow != null) {

                            sceneWindow.setStatus(
                                    "Szenenwechsel gefunden bei Frame "
                                    + foundFrame);
                        }

                    });


                    searching = false;
                    return;
                }


                previous = current;


                try {

                    Thread.sleep(5);

                } catch (InterruptedException e) {

                    Thread.currentThread().interrupt();
                    break;
                }
            }


            searching = false;


        });


        searchThread.setName(
                "SceneDetectionThread");


        searchThread.start();
    }
}
