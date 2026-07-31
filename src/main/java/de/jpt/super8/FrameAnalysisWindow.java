package de.jpt.super8;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import javax.swing.border.EmptyBorder;

import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.OpenCVFrameConverter;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.Rect;

/**
 * Iteratives Analysefenster.
 *
 * <p>Laeuft mehrfach ueber das gesamte Video und sammelt pro Frame ein
 * {@link FrameInfo}. Zwei Fortschrittsbalken zeigen den Status des
 * aktuellen Laufs sowie die Summe aller Laeufe.</p>
 *
 * <p>Aktuell implementiert: <b>Lauf 1</b> – findet in jedem Frame die
 * lokalen Minima des vertikalen Helligkeitsprofils im OBEREN und UNTEREN
 * Drittel (siehe {@link Super8Pipeline}/{@link ImageOps#verticalCrop(Mat,int)})
 * und prueft, ob deren Abstand innerhalb {@link #getTargetDistance()}
 * &plusmn; {@link #DIST_TOL_PX} Pixel liegt. Andernfalls wird der Frame
 * als <code>bad</code> markiert. Weitere Laeufe folgen.</p>
 */
public class FrameAnalysisWindow extends JFrame {

    private static final long serialVersionUID = 1L;

    /**
     * Default-Zielabstand zwischen oberer und unterer Frame-Kante in Pixel.
     * Entspricht dem Wert aus {@code Config.verticalFrameHeight}.
     */
    public static final int DEFAULT_TARGET_DISTANCE = 920;

    /** Breite (Pixel) des linken Streifens, in dem das Pilotloch gesucht wird. */
    public static final int HOLE_BAND_WIDTH = 80;

    /** Schwellwert fuer die Loch-Detektion (Anteil der maximalen Zeilenhelligkeit). */
    public static final double HOLE_BRIGHTNESS_FRAC = 0.90;

    /** Erlaubte Toleranz (Pixel) fuer den Abstand oben/unten. */
    public static final int DIST_TOL_PX = 10;

    // ---- Video ----
    private final File videoFile;
    private final int totalFrames;
    private final double fps;

    // ---- Ergebnis ----
    private final List<FrameInfo> frameInfos = new ArrayList<>();
    private final DefaultListModel<FrameInfo> listModel = new DefaultListModel<>();
    private final JList<FrameInfo> resultList = new JList<>(listModel);

    // ---- UI ----
    private final JProgressBar barCurrent = new JProgressBar();
    private final JProgressBar barTotal   = new JProgressBar();
    private final JLabel lblPass   = new JLabel(" ");
    private final JLabel lblStatus = new JLabel(" ");
    private final JLabel lblResults = new JLabel(" ");
    private final JButton btnStart  = new JButton("Start");
    private final JButton btnCancel = new JButton("Abbrechen");

    // ---- Steuerung ----
    private volatile boolean running   = false;
    private volatile boolean cancelled = false;
    private Thread worker;

    // ---- Parameter ----
    private int targetDistance = DEFAULT_TARGET_DISTANCE;
    /** Anzahl geplanter Laeufe. Aktuell nur Lauf 1 implementiert. */
    private int numPasses = 1;

    /** Mittelwert des Min-Abstands (top-bottom) nach Lauf 1. NaN wenn noch nicht berechnet. */
    private volatile double avgMinDistance = Double.NaN;

    /**
     * @param videoFile   zu analysierende Datei
     * @param totalFrames Gesamtzahl der Frames (fuer Progressbars) – aus {@link VideoInfo}
     * @param fps         Framerate – aus {@link VideoInfo}
     */
    public FrameAnalysisWindow(File videoFile, int totalFrames, double fps) {
        super("Frame Analysis - " + videoFile.getName());
        this.videoFile = videoFile;
        this.totalFrames = Math.max(0, totalFrames);
        this.fps = fps > 0 ? fps : 24.0;

        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        buildGui();

        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosing(java.awt.event.WindowEvent e) {
                cancel();
            }
        });
    }

    // ------------------------------------------------------------------ GUI

    private void buildGui() {
        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBorder(new EmptyBorder(10, 10, 10, 10));

        // Progress-Bereich
        JPanel top = new JPanel(new GridLayout(5, 1, 4, 4));
        top.add(lblPass);

        barCurrent.setStringPainted(true);
        barCurrent.setMinimum(0);
        barCurrent.setMaximum(Math.max(1, totalFrames));
        top.add(labeledBar("Aktueller Lauf:", barCurrent));

        barTotal.setStringPainted(true);
        barTotal.setMinimum(0);
        barTotal.setMaximum(Math.max(1, totalFrames * numPasses));
        top.add(labeledBar("Gesamt:", barTotal));

        top.add(lblStatus);
        top.add(lblResults);
        root.add(top, BorderLayout.NORTH);

        // Ergebnisliste
        resultList.setCellRenderer(new FrameInfoRenderer());
        JScrollPane sp = new JScrollPane(resultList);
        sp.setPreferredSize(new Dimension(720, 380));
        sp.setBorder(BorderFactory.createTitledBorder("Frame-Infos"));
        root.add(sp, BorderLayout.CENTER);

        // Buttons
        JPanel south = new JPanel();
        south.add(btnStart);
        south.add(btnCancel);
        btnCancel.setEnabled(false);
        root.add(south, BorderLayout.SOUTH);

        setContentPane(root);
        pack();
        setLocationByPlatform(true);

        btnStart.addActionListener(e -> start());
        btnCancel.addActionListener(e -> cancel());

        updatePassLabel(0);
    }

    private static JPanel labeledBar(String label, JProgressBar bar) {
        JPanel p = new JPanel(new BorderLayout(6, 0));
        JLabel l = new JLabel(label);
        l.setPreferredSize(new Dimension(120, 20));
        p.add(l, BorderLayout.WEST);
        p.add(bar, BorderLayout.CENTER);
        return p;
    }

    // ------------------------------------------------------------ Parameter

    public void setTargetDistance(int px) { this.targetDistance = Math.max(1, px); }
    public int  getTargetDistance()       { return targetDistance; }

    public void setNumPasses(int n) {
        this.numPasses = Math.max(1, n);
        barTotal.setMaximum(Math.max(1, totalFrames * numPasses));
        updatePassLabel(0);
    }
    public int getNumPasses() { return numPasses; }

    /** Live-View auf die bislang gesammelten Ergebnisse. */
    public List<FrameInfo> getFrameInfos() { return Collections.unmodifiableList(frameInfos); }

    /** Mittelwert des Min-Abstands (top-bottom) nach Lauf 1; NaN wenn noch nicht berechnet. */
    public double getAvgMinDistance() { return avgMinDistance; }

    /**
     * Liefert die {@link FrameInfo} zur angegebenen Framenummer, oder {@code null}
     * wenn noch nicht analysiert. Thread-safe fuer Lese-Zugriff waehrend Lauf 1.
     */
    public FrameInfo getFrameInfo(int frameNumber) {
        if (frameNumber < 0) return null;
        List<FrameInfo> list = frameInfos;
        if (frameNumber >= list.size()) return null;
        try {
            return list.get(frameNumber);
        } catch (Exception ignore) {
            return null;
        }
    }

    // ------------------------------------------------------------- Ablauf

    public void start() {
        if (running) return;
        running = true;
        cancelled = false;
        btnStart.setEnabled(false);
        btnCancel.setEnabled(true);
        frameInfos.clear();
        listModel.clear();
        barCurrent.setValue(0);
        barTotal.setValue(0);
        lblStatus.setText("laeuft...");

        worker = new Thread(this::runPasses, "FrameAnalysisWorker");
        worker.setDaemon(true);
        worker.start();
    }

    public void cancel() { cancelled = true; }

    private void runPasses() {
        try {
            for (int pass = 1; pass <= numPasses && !cancelled; pass++) {
                final int passFinal = pass;
                SwingUtilities.invokeLater(() -> {
                    updatePassLabel(passFinal);
                    barCurrent.setValue(0);
                });
                if (pass == 1) {
                    runPass1();
                }
                // Weitere Laeufe (pass >= 2) folgen spaeter.
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            SwingUtilities.invokeLater(() ->
                    lblStatus.setText("Fehler: " + ex.getMessage()));
        } finally {
            running = false;
            final int total = frameInfos.size();
            final long bad = frameInfos.stream().filter(fi -> fi.bad).count();
            SwingUtilities.invokeLater(() -> {
                btnStart.setEnabled(true);
                btnCancel.setEnabled(false);
                lblStatus.setText(cancelled
                        ? String.format("Abgebrochen. Verarbeitet: %d Frames (bad: %d)", total, bad)
                        : String.format("Fertig. %d Frames (bad: %d)", total, bad));
            });
        }
    }

    private void updatePassLabel(int currentPass) {
        lblPass.setText(String.format("Lauf %d / %d   |   Ziel-Abstand: %d px (±%d)",
                currentPass, numPasses, targetDistance, DIST_TOL_PX));
    }

    // ------------------------------------------------------------- Lauf 1

    /**
     * Lauf 1: vertikale Randminima pro Frame bestimmen und {@link FrameInfo}s
     * fuellen. Nutzt {@link FFmpegFrameGrabber} fuer schnelles sequentielles
     * Lesen (analog zu {@link Super8Pipeline}).
     */
    private void runPass1() throws Exception {
        try (FFmpegFrameGrabber g = new FFmpegFrameGrabber(videoFile.getAbsolutePath());
             OpenCVFrameConverter.ToMat conv = new OpenCVFrameConverter.ToMat()) {
            g.start();

            final int passOffset = 0; // Lauf 1 -> 0 * totalFrames
            int idx = 0;
            final List<FrameInfo> batch = new ArrayList<>();

            Frame fr;
            while (!cancelled && (fr = g.grabImage()) != null) {
                Mat bgr = conv.convert(fr);
                if (bgr == null) { idx++; continue; }

                long ts = (long) (idx * 1000.0 / fps);
                FrameInfo fi = new FrameInfo(idx, ts);

                Mat gray = ImageOps.toGray(bgr);
                try {
                    analyzeFirstPass(gray, fi);
                } finally {
                    gray.close();
                }

                frameInfos.add(fi);
                batch.add(fi);

                if (batch.size() >= 25) {
                    flushBatch(batch, idx + 1, passOffset);
                }

                final int cur = idx + 1;
                SwingUtilities.invokeLater(() -> {
                    barCurrent.setValue(cur);
                    barTotal.setValue(passOffset + cur);
                });
                idx++;
            }
            g.stop();

            if (!batch.isEmpty()) {
                flushBatch(batch, idx, passOffset);
            }
        }

        // Durchschnittsabstaende ueber alle verarbeiteten Frames
        computeAndShowAveragesPass1();
    }

    /**
     * Aggregiert nach Lauf 1: durchschnittlicher Abstand der Minima (top-bottom),
     * sowie – falls Pilotloch erkannt wurde – die durchschnittlichen Abstaende
     * Loch-Mitte zu oberem und unterem Minimum.
     */
    private void computeAndShowAveragesPass1() {
        long nDist = 0, sumDist = 0;
        long nHT   = 0, sumHT   = 0;
        long nHB   = 0, sumHB   = 0;
        long nH    = 0, sumH    = 0;
        long nHole = 0;
        long bad   = 0;

        for (FrameInfo fi : frameInfos) {
            if (fi.bad) bad++;
            if (fi.distance > 0) { sumDist += fi.distance; nDist++; }
            if (fi.holeCenter >= 0) {
                nHole++;
                if (fi.holeHeight > 0) { sumH += fi.holeHeight; nH++; }
            }
            if (fi.holeToTopMin    != Integer.MIN_VALUE) { sumHT += fi.holeToTopMin;    nHT++; }
            if (fi.holeToBottomMin != Integer.MIN_VALUE) { sumHB += fi.holeToBottomMin; nHB++; }
        }

        final double avgDist = nDist > 0 ? (double) sumDist / nDist : Double.NaN;
        this.avgMinDistance = avgDist;
        final double avgHT   = nHT   > 0 ? (double) sumHT   / nHT   : Double.NaN;
        final double avgHB   = nHB   > 0 ? (double) sumHB   / nHB   : Double.NaN;
        final double avgH    = nH    > 0 ? (double) sumH    / nH    : Double.NaN;
        final long   nHoleF  = nHole;
        final long   badF    = bad;
        final int    total   = frameInfos.size();

        SwingUtilities.invokeLater(() -> lblResults.setText(String.format(
                "<html>Ø Min-Abstand: %s px &nbsp;|&nbsp; Loch erkannt: %d/%d " +
                "&nbsp;|&nbsp; Ø Lochhöhe: %s px &nbsp;|&nbsp; " +
                "Ø d(Loch,Top): %s px &nbsp;|&nbsp; Ø d(Bot,Loch): %s px " +
                "&nbsp;|&nbsp; bad: %d</html>",
                fmt(avgDist), nHoleF, total, fmt(avgH), fmt(avgHT), fmt(avgHB), badF)));

        System.out.println("=== Lauf 1 Zusammenfassung ===");
        System.out.printf("  Frames:            %d (bad: %d)%n", total, badF);
        System.out.printf("  Ø Min-Abstand:     %s px  (n=%d)%n", fmt(avgDist), nDist);
        System.out.printf("  Loch erkannt:      %d / %d%n", nHoleF, total);
        System.out.printf("  Ø Lochhöhe:        %s px  (n=%d)%n", fmt(avgH), nH);
        System.out.printf("  Ø d(Loch,Top):     %s px  (n=%d)%n", fmt(avgHT), nHT);
        System.out.printf("  Ø d(Bot,Loch):     %s px  (n=%d)%n", fmt(avgHB), nHB);
    }

    private static String fmt(double v) {
        return Double.isNaN(v) ? "--" : String.format("%.1f", v);
    }

    private void runPass1Body() {
        // Placeholder um Struktur zu erhalten (kein Aufruf).
    }

    private void runPass1Dummy() {
        // reserved
    }

    // Ende runPass1-Erweiterungen
    private void closeAveragesBlock() {
        // no-op
    }

    /** Kopiert die aktuellen Batch-Elemente in das Listen-Model (auf dem EDT). */
    private void flushBatch(List<FrameInfo> batch, int processed, int passOffset) {
        final List<FrameInfo> copy = new ArrayList<>(batch);
        batch.clear();
        SwingUtilities.invokeLater(() -> {
            for (FrameInfo fi : copy) listModel.addElement(fi);
            lblStatus.setText(String.format("Verarbeitet: %d Frames", processed));
        });
    }

    /**
     * Sucht im vertikalen Helligkeitsprofil ({@link ImageOps#verticalProfile(Mat)})
     * strikte lokale Minima im OBEREN Drittel {@code [0, h/3)} und im UNTEREN
     * Drittel {@code [2h/3, h)}. Waehlt das Paar mit dem Abstand am naechsten
     * zu {@link #getTargetDistance()} und markiert den Frame als
     * <code>bad</code>, wenn:
     * <ul>
     *   <li>in einem der beiden Drittel keine Minima existieren, oder</li>
     *   <li>der beste Abstand ausserhalb {@code targetDistance ± DIST_TOL_PX} liegt.</li>
     * </ul>
     */
    private void analyzeFirstPass(Mat gray, FrameInfo fi) {
        int h = gray.rows();
        if (h < 6) { fi.bad = true; fi.badReason = "frame too small"; return; }

        double[] prof = ImageOps.verticalProfile(gray);

        int topEnd       = h / 3;         // oberes Drittel: [0, topEnd)
        int bottomStart  = h - h / 3;     // unteres Drittel: [bottomStart, h)

        List<Integer> topMinima    = strictLocalMinima(prof, 1,           topEnd);
        List<Integer> bottomMinima = strictLocalMinima(prof, bottomStart, prof.length - 1);

        if (topMinima.isEmpty() || bottomMinima.isEmpty()) {
            fi.bad = true;
            if (topMinima.isEmpty() && bottomMinima.isEmpty()) {
                fi.badReason = "no minima in top/bottom third";
            } else if (topMinima.isEmpty()) {
                fi.badReason = "no minimum in top third";
            } else {
                fi.badReason = "no minimum in bottom third";
            }
            return;
        }

        // Bestes Paar: Abstand am naechsten zu targetDistance
        int bestTop = topMinima.get(0);
        int bestBottom = bottomMinima.get(0);
        int bestErr = Integer.MAX_VALUE;
        for (int t : topMinima) {
            for (int b : bottomMinima) {
                int d   = b - t;
                int err = Math.abs(d - targetDistance);
                if (err < bestErr) {
                    bestErr = err;
                    bestTop = t;
                    bestBottom = b;
                }
            }
        }

        fi.topMinRow    = bestTop;
        fi.bottomMinRow = bestBottom;
        fi.distance     = bestBottom - bestTop;

        if (bestErr > DIST_TOL_PX) {
            fi.bad = true;
            fi.badReason = String.format("distance %d out of %d ± %d",
                    fi.distance, targetDistance, DIST_TOL_PX);
        }

        // Pilotloch-Detektion im linken Streifen
        detectPilotHole(gray, fi);
    }

    /**
     * Pilotloch-Detektion: nimmt die linken {@link #HOLE_BAND_WIDTH} Pixel des Frames,
     * berechnet je Zeile die Durchschnittshelligkeit und findet den laengsten
     * zusammenhaengenden Bereich, dessen Zeilenhelligkeit mindestens
     * {@link #HOLE_BRIGHTNESS_FRAC} des Maximums erreicht.
     *
     * <p>Setzt {@link FrameInfo#holeTop}, {@link FrameInfo#holeBottom},
     * {@link FrameInfo#holeCenter}, {@link FrameInfo#holeHeight} sowie die
     * Abstaende {@link FrameInfo#holeToTopMin} und {@link FrameInfo#holeToBottomMin}.</p>
     */
    private void detectPilotHole(Mat gray, FrameInfo fi) {
        int h = gray.rows();
        int w = gray.cols();
        int bandW = Math.min(HOLE_BAND_WIDTH, w);
        if (bandW < 4 || h < 4) return;

        Mat band = new Mat(gray, new Rect(0, 0, bandW, h));
        double[] rm;
        try {
            rm = ImageOps.rowMeans(band);
        } finally {
            band.close();
        }

        // Maximum bestimmen
        double max = 0;
        for (double v : rm) if (v > max) max = v;
        if (max <= 1e-6) return; // komplett schwarz

        double th = HOLE_BRIGHTNESS_FRAC * max;

        // Laengsten zusammenhaengenden Bereich >= th finden
        int bestStart = -1, bestEnd = -1, bestLen = 0;
        int curStart  = -1;
        for (int i = 0; i < rm.length; i++) {
            if (rm[i] >= th) {
                if (curStart < 0) curStart = i;
            } else if (curStart >= 0) {
                int len = i - curStart;
                if (len > bestLen) { bestLen = len; bestStart = curStart; bestEnd = i - 1; }
                curStart = -1;
            }
        }
        if (curStart >= 0) {
            int len = rm.length - curStart;
            if (len > bestLen) { bestLen = len; bestStart = curStart; bestEnd = rm.length - 1; }
        }
        if (bestStart < 0 || bestLen < 1) return;

        fi.holeTop    = bestStart;
        fi.holeBottom = bestEnd;
        fi.holeCenter = (bestStart + bestEnd) / 2;
        fi.holeHeight = bestLen;

        // Relation zu den vorher gefundenen Minima (nur wenn vorhanden)
        if (fi.topMinRow    >= 0) fi.holeToTopMin    = fi.holeCenter - fi.topMinRow;
        if (fi.bottomMinRow >= 0) fi.holeToBottomMin = fi.bottomMinRow - fi.holeCenter;
    }

    /**
     * Liefert Indizes strikter lokaler Minima im halboffenen Bereich
     * {@code [from, toExclusive)}. Definition: {@code prof[i] < prof[i-1] && prof[i] <= prof[i+1]}
     * – identisch zu {@link ImageOps#verticalCrop(Mat, int)}.
     */
    private static List<Integer> strictLocalMinima(double[] prof, int from, int toExclusive) {
        List<Integer> out = new ArrayList<>();
        int lo = Math.max(1, from);
        int hi = Math.min(prof.length - 1, toExclusive);
        for (int i = lo; i < hi; i++) {
            if (prof[i] < prof[i - 1] && prof[i] <= prof[i + 1]) out.add(i);
        }
        return out;
    }

    // ---------------------------------------------------- Renderer

    private static class FrameInfoRenderer extends DefaultListCellRenderer {
        private static final long serialVersionUID = 1L;
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value,
                int index, boolean isSelected, boolean cellHasFocus) {
            Component c = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof FrameInfo && !isSelected) {
                FrameInfo fi = (FrameInfo) value;
                c.setForeground(fi.bad ? new Color(200, 30, 30) : Color.BLACK);
            }
            return c;
        }
    }
}