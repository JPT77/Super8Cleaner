package de.jpt.super8 ;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public class BrightnessHistoryPanel extends JPanel {

    private static final int LEFT = 50;
    private static final int RIGHT = 20;
    private static final int TOP = 20;
    private static final int BOTTOM = 35;

    private final List<Double> history;

    public BrightnessHistoryPanel(List<Double> history) {
        this.history = history;

        setPreferredSize(new Dimension(900, 300));
        setBackground(Color.WHITE);
    }

    @Override
    protected void paintComponent(Graphics g) {

        super.paintComponent(g);

        Graphics2D g2 = (Graphics2D) g.create();

        g2.setRenderingHint(
                RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();

        int graphW = w - LEFT - RIGHT;
        int graphH = h - TOP - BOTTOM;

        // Hintergrund

        g2.setColor(Color.WHITE);
        g2.fillRect(0, 0, w, h);

        // Achsen

        g2.setColor(Color.BLACK);

        g2.drawLine(
                LEFT,
                TOP,
                LEFT,
                TOP + graphH);

        g2.drawLine(
                LEFT,
                TOP + graphH,
                LEFT + graphW,
                TOP + graphH);

        if (history.isEmpty()) {

            g2.dispose();
            return;
        }

        // ============================================
        // Nur so viele Frames wie Pixel vorhanden sind
        // ============================================

        int visibleFrames = Math.max(2, graphW);

        int start = Math.max(
                0,
                history.size() - visibleFrames);

        int end = history.size();

        // ============================================
        // Min / Max der sichtbaren Daten
        // ============================================

        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;

        for (int i = start; i < end; i++) {

            double v = history.get(i);

            if (v < min)
                min = v;

            if (v > max)
                max = v;
        }

        if (Math.abs(max - min) < 0.0001) {
            max += 1;
            min -= 1;
        }

        double margin = (max - min) * 0.05;

        max += margin;
        min -= margin;

        // ============================================
        // Grid
        // ============================================

        g2.setColor(new Color(230,230,230));

        for (int i = 0; i <= 10; i++) {

            int y = TOP + graphH * i / 10;

            g2.drawLine(
                    LEFT,
                    y,
                    LEFT + graphW,
                    y);
        }

        // ============================================
        // Y-Beschriftung
        // ============================================

        g2.setColor(Color.BLACK);

        FontMetrics fm = g2.getFontMetrics();

        for (int i = 0; i <= 5; i++) {

            double value =
                    max - (max - min) * i / 5.0;

            int y =
                    TOP + graphH * i / 5;

            String s =
                    String.format("%.1f", value);

            g2.drawString(
                    s,
                    LEFT - fm.stringWidth(s) - 5,
                    y + 5);
        }

        // ============================================
        // Kurve
        // ============================================

        g2.setColor(new Color(40,90,220));

        int prevX = -1;
        int prevY = -1;

        for (int i = start; i < end; i++) {

            double value = history.get(i);

            int x =
                    LEFT + (i - start);

            int y =
                    TOP +
                    graphH -
                    (int) ((value - min) / (max - min) * graphH);

            if (prevX != -1) {

                g2.drawLine(
                        prevX,
                        prevY,
                        x,
                        y);
            }

            prevX = x;
            prevY = y;
        }

        // ============================================
        // Aktueller Punkt
        // ============================================

        g2.setColor(Color.RED);

        g2.fillOval(
                prevX - 4,
                prevY - 4,
                8,
                8);

        // ============================================
        // Rechte vertikale Linie
        // ============================================

        g2.drawLine(
                prevX,
                TOP,
                prevX,
                TOP + graphH);

        // ============================================
        // Titel
        // ============================================

        g2.setColor(Color.BLACK);

        g2.drawString(
                "Verlauf der durchschnittlichen Helligkeit",
                LEFT,
                15);

        // ============================================
        // X-Beschriftung
        // ============================================

        String left =
                Integer.toString(start);

        String right =
                Integer.toString(end - 1);

        g2.drawString(
                left,
                LEFT,
                h - 8);

        g2.drawString(
                right,
                LEFT + graphW - fm.stringWidth(right),
                h - 8);

        g2.dispose();
    }
}