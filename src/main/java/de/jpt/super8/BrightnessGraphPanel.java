package de.jpt.super8;

import java.awt.*;
import javax.swing.JPanel;

public class BrightnessGraphPanel extends JPanel {

    private static final long serialVersionUID = 1L;

	public enum Orientation {
        HORIZONTAL,
        VERTICAL
    }

    private double[] values;
    private Orientation orientation = Orientation.HORIZONTAL;

    public BrightnessGraphPanel(Orientation orientation) {
		this.orientation = orientation;
	}

	public void setValues(double[] values) {
        this.values = values;
        repaint();
    }

    public void setOrientation(Orientation orientation) {
        this.orientation = orientation;
        repaint();
    }

    public Orientation getOrientation() {
        return orientation;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        if (values == null || values.length < 2)
            return;

        Graphics2D g2 = (Graphics2D) g;

        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);

        g2.setColor(Color.BLACK);
        g2.fillRect(0, 0, getWidth(), getHeight());

        g2.setColor(Color.GREEN);

        if (orientation == Orientation.HORIZONTAL) {
            paintHorizontal(g2);
        } else {
            paintVertical(g2);
        }
    }

    private void paintHorizontal(Graphics2D g2) {

        int w = getWidth();
        int h = getHeight();

        double sx = (double) w / (values.length - 1);

        int lastX = 0;
        int lastY = scale(values[0], h);

        for (int i = 1; i < values.length; i++) {

            int x = (int) Math.round(i * sx);
            int y = scale(values[i], h);

            g2.drawLine(lastX, lastY, x, y);

            lastX = x;
            lastY = y;
        }
    }

    private void paintVertical(Graphics2D g2) {

        int w = getWidth();
        int h = getHeight();

        double sy = (double) h / (values.length - 1);

        int lastX = scale(values[0], w);
        int lastY = 0;

        for (int i = 1; i < values.length; i++) {

            int x = scale(values[i], w);
            int y = (int) Math.round(i * sy);

            g2.drawLine(lastX, lastY, x, y);

            lastX = x;
            lastY = y;
        }
    }

    /**
     * Skaliert die Helligkeit (0..255) auf die verfügbare Größe.
     */
    private int scale(double value, int size) {
        value = Math.max(0, Math.min(255, value));
        return size - 1 - (int) Math.round(value * (size - 1) / 255.0);
    }
}