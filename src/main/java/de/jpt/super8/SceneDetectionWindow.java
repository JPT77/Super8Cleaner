package de.jpt.super8;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SceneDetectionWindow extends JFrame {

    // ----------------------------
    // Statistik
    // ----------------------------

    private final JLabel frameLabel = new JLabel();
    private final JLabel brightnessLabel = new JLabel();
    private final JLabel previousBrightnessLabel = new JLabel();
    private final JLabel differenceLabel = new JLabel();
    private final JLabel meanLabel = new JLabel();
    private final JLabel stdDevLabel = new JLabel();
    private final JLabel zScoreLabel = new JLabel();
    private final JLabel thresholdLabel = new JLabel();
    private final JLabel sceneLabel = new JLabel();

    private JButton searchButton;
    private JButton stopButton;
    private JLabel statusLabel = new JLabel("XXX");

    // ----------------------------
    // Daten
    // ----------------------------

    private final List<Double> brightnessHistory = new ArrayList<>();
    private final List<Double> differenceHistory = new ArrayList<>();

    private final BrightnessHistoryPanel historyPanel =
            new BrightnessHistoryPanel(brightnessHistory);

    private final GaussianPanel gaussianPanel =
            new GaussianPanel(brightnessHistory);

    private double threshold;

    public SceneDetectionWindow(double threshold) {

        super("Scene Detection Debug");

        this.threshold = threshold;

        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setSize(1000, 800);
        setLocationRelativeTo(null);

        setLayout(new BorderLayout(8, 8));

        JPanel info = createInfoPanel();

        JSplitPane split =
                new JSplitPane(
                        JSplitPane.VERTICAL_SPLIT,
                        gaussianPanel,
                        historyPanel);

        split.setResizeWeight(0.35);
        split.setBorder(null);

        add(info, BorderLayout.NORTH);
        add(split, BorderLayout.CENTER);

        JPanel buttons = new JPanel();
        searchButton = new JButton("Weiter suchen");
        stopButton = new JButton("Pause");
        buttons.add(searchButton);
        buttons.add(stopButton);
//        add(buttons, BorderLayout.SOUTH);

        JPanel bottom = new JPanel(new BorderLayout());
        bottom.add(statusLabel, BorderLayout.CENTER);
        bottom.add(buttons, BorderLayout.SOUTH);
        add(bottom, BorderLayout.SOUTH);

        stopButton.addActionListener(e -> {

            if(searchListener != null)
                stopListener.run();

        });
    }

    private JPanel createInfoPanel() {

        JPanel p = new JPanel(new GridLayout(3, 3, 12, 6));
        p.setBorder(new EmptyBorder(10,10,10,10));

        p.add(frameLabel);
        p.add(brightnessLabel);
        p.add(previousBrightnessLabel);

        p.add(differenceLabel);
        p.add(meanLabel);
        p.add(stdDevLabel);

        p.add(zScoreLabel);
        p.add(thresholdLabel);
        p.add(sceneLabel);

        return p;
    }

    /**
     * Wird für jeden untersuchten Frame aufgerufen.
     */
    public void updateValues(
            int frame,
            double currentBrightness,
            double previousBrightness,
            double frameStdDev,
            double difference,
            double threshold) {

        this.threshold = threshold;

        brightnessHistory.add(currentBrightness);

        differenceHistory.add(difference);

        double mean = mean(brightnessHistory);
        double std = stdDev(brightnessHistory, mean);

        double z =
                std > 1e-9
                        ? (currentBrightness - mean) / std
                        : 0;

        frameLabel.setText(
                String.format("Frame: %d", frame));

        brightnessLabel.setText(
                String.format("Aktuelle Helligkeit: %.2f", currentBrightness));

        previousBrightnessLabel.setText(
                String.format("Vorherige Helligkeit: %.2f", previousBrightness));

        differenceLabel.setText(
                String.format("Differenz: %.2f", difference));

        meanLabel.setText(
                String.format("μ = %.2f", mean));

        stdDevLabel.setText(
                String.format("σ = %.2f", std));

        zScoreLabel.setText(
                String.format("z = %.2f", z));

        thresholdLabel.setText(
                String.format("Threshold = %.2f", threshold));

        boolean scene = difference >= threshold;

        sceneLabel.setText(
                scene
                        ? "Szenenwechsel: JA"
                        : "Szenenwechsel: Nein");

        sceneLabel.setForeground(
                scene
                        ? Color.RED
                        : new Color(0,140,0));

        historyPanel.repaint();
        gaussianPanel.repaint();
    }

    public List<Double> getBrightnessHistory() {
        return Collections.unmodifiableList(brightnessHistory);
    }

    public List<Double> getDifferenceHistory() {
        return Collections.unmodifiableList(differenceHistory);
    }

    public double getThreshold() {
        return threshold;
    }

    // ----------------------------------------------------
    // Statistik
    // ----------------------------------------------------

    private static double mean(List<Double> values) {

        if(values.isEmpty())
            return 0;

        double sum = 0;

        for(double v : values)
            sum += v;

        return sum / values.size();
    }

    private static double stdDev(
            List<Double> values,
            double mean) {

        if(values.size() < 2)
            return 0;

        double sum = 0;

        for(double v : values) {

            double d = v - mean;
            sum += d * d;
        }

        return Math.sqrt(sum / values.size());
    }

    private Runnable searchListener;
    private Runnable stopListener;


    public void setSearchAction(Runnable r) {

        searchListener=r;

        searchButton.addActionListener(
                e -> r.run());
    }

    public void setStopAction(Runnable r) {

        stopListener=r;
    }

    public void setStatus(String text) {
        SwingUtilities.invokeLater(() -> {
            statusLabel.setText(text);
        });
    }

}