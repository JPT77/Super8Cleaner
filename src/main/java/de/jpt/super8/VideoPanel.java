package de.jpt.super8;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.image.BufferedImage;

import javax.swing.JPanel;

import org.opencv.core.CvType;
import org.opencv.core.Mat;

public class VideoPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    private BufferedImage image;

    // ---- Overlay (Ergebnisse aus FrameAnalysisWindow) ----
    private FrameInfo overlayInfo;
    private double    overlayAvgDist = Double.NaN;

    private static final Color COL_DETECTED  = new Color(255, 140,   0); // orange
    private static final Color COL_CORRECTED = new Color(  0, 220,  60); // gruen
    private static final Color COL_HOLE      = new Color(220,  60, 220); // magenta
    private static final Color COL_BORDER_UP = new Color(  0, 200, 220); // cyan  (oberes Band)
    private static final Color COL_BORDER_DN = new Color(240, 200,   0); // gelb  (unteres Band)
    private static final Color COL_BG_LABEL  = new Color(  0,   0,   0, 180);

    // Halbtransparente Farben fuer die Vektor-Plots
    private static final Color PLOT_VPROFILE  = new Color(255, 200, 120, 180); // hellorange
    private static final Color PLOT_HOLE      = new Color(255, 150, 220, 180); // hellmagenta
    private static final Color PLOT_UPPERBAND = new Color(120, 220, 240, 180); // hellcyan
    private static final Color PLOT_LOWERBAND = new Color(255, 235, 120, 180); // hellgelb
    /** Maximale Amplitude der Plot-Kurve in Panel-Pixeln. */
    private static final int   PLOT_AMPLITUDE = 60;

    public VideoPanel() {
        setBackground(Color.BLACK);
    }

    public void setImage(BufferedImage img) {
        image = img;
        if (img != null) {
            setPreferredSize(new Dimension(img.getWidth(), img.getHeight()));
        }
        revalidate();
        repaint();
    }

    public BufferedImage getImage() {
        return image;
    }

    public Mat getImageAsMat() {
        if (image == null)
            return new Mat();

        BufferedImage img = image;
        // Sicherstellen, dass TYPE_3BYTE_BGR vorliegt
        if (img.getType() != BufferedImage.TYPE_3BYTE_BGR) {
            BufferedImage converted = new BufferedImage(
                    img.getWidth(),
                    img.getHeight(),
                    BufferedImage.TYPE_3BYTE_BGR);

            Graphics2D g = converted.createGraphics();
            g.drawImage(img, 0, 0, null);
            g.dispose();

            img = converted;
        }

        byte[] pixels = ((java.awt.image.DataBufferByte)
                img.getRaster().getDataBuffer()).getData();

        Mat mat = new Mat(img.getHeight(), img.getWidth(), CvType.CV_8UC3);
        mat.put(0, 0, pixels);

        return mat;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (image == null) {
            return;
        }
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);

        int pw = getWidth();
        int ph = getHeight();
        int iw = image.getWidth();
        int ih = image.getHeight();
        double scale = Math.min(
                1.0,
                Math.min((double)pw / iw,
                         (double)ph / ih));
        int w = (int) (iw * scale);
        int h = (int) (ih * scale);
        int x = (pw - w) / 2;
        int y = (ph - h) / 2;
        g2.drawImage(image, x, y, w, h, null);

        // ----- Analyse-Overlay -----
        if (overlayInfo != null) {
            drawAnalysisOverlay(g2, x, y, w, h, scale);
        }

        // ----- Debug-Text -----
        double panelAspect = (double) pw / ph;
        double imageAspect = (double) iw / ih;

        g2.setColor(new Color(0, 0, 0, 180)); // halbtransparenter Hintergrund
        g2.fillRoundRect(10, 10, 120, 120, 10, 10);

        g2.setColor(Color.WHITE);
        g2.drawString(String.format("Panel: %d x %d", pw, ph), 20, 30);
        g2.drawString(String.format("Bild : %d x %d", iw, ih), 20, 50);
        g2.drawString(String.format("Panel AR: %.3f", panelAspect), 20, 70);
        g2.drawString(String.format("Bild  AR: %.3f", imageAspect), 20, 90);
        g2.drawString(String.format("Scale: %.3f", scale), 20, 110);
    }

    // ----------------------------------------------------------------- Overlay

    /**
     * Zeichnet Analyse-Ergebnisse als horizontale Linien mit Beschriftung.
     * Farbcodierung:
     * <ul>
     *   <li>ORANGE = erkannte Grenzen ({@code topMinRow}, {@code bottomMinRow})</li>
     *   <li>GRUEN  = korrigierte Grenzen bei Abweichung vom Ziel-Abstand
     *              (Delta wird symmetrisch aufgeteilt, oben +/- und unten -/+)</li>
     *   <li>MAGENTA = Pilotloch oben und unten</li>
     * </ul>
     */
    private void drawAnalysisOverlay(Graphics2D g2, int ox, int oy, int iw, int ih, double scale) {
        FrameInfo fi = overlayInfo;
        if (fi == null || image == null) return;

        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        Stroke oldStroke = g2.getStroke();
        g2.setStroke(new BasicStroke(1.5f));

        // ----- Helligkeitsvektoren (unter den Detektionslinien) -----
        // Full-Frame Vertikalprofil (Lauf 1: top/bot) – am RECHTEN Bildrand
        drawVerticalPlot(g2, fi.verticalProfile, ox + iw, oy, ih, scale,
                -PLOT_AMPLITUDE, PLOT_VPROFILE); // Amplitude nach LINKS in den Bildbereich hinein
        // Zeilenmittel linker 80-px-Streifen (Lauf 1: Loch) – am LINKEN Bildrand
        drawVerticalPlot(g2, fi.holeRowMeans, ox, oy, ih, scale,
                +PLOT_AMPLITUDE, PLOT_HOLE);
        // Spaltenmittel oberes Band (Lauf 2)
        if (fi.upperBandColMeans != null && fi.topMinRow >= 0 && fi.holeTop >= 0) {
            int bandMidRow = (fi.topMinRow + fi.holeTop) / 2;
            drawHorizontalPlot(g2, fi.upperBandColMeans, ox, iw, oy, scale,
                    bandMidRow, PLOT_AMPLITUDE, PLOT_UPPERBAND);
        }
        // Spaltenmittel unteres Band (Lauf 2)
        if (fi.lowerBandColMeans != null && fi.holeBottom >= 0 && fi.bottomMinRow >= 0) {
            int bandMidRow = (fi.holeBottom + fi.bottomMinRow) / 2;
            drawHorizontalPlot(g2, fi.lowerBandColMeans, ox, iw, oy, scale,
                    bandMidRow, PLOT_AMPLITUDE, PLOT_LOWERBAND);
        }

        // Erkannte Grenzen (orange)
        if (fi.topMinRow >= 0) {
            drawHOverlayLine(g2, ox, oy, iw, ih, scale, fi.topMinRow,
                    COL_DETECTED, "top=" + fi.topMinRow, true);
        }
        if (fi.bottomMinRow >= 0) {
            String lbl = "bot=" + fi.bottomMinRow
                       + (fi.distance > 0 ? "  dist=" + fi.distance : "");
            drawHOverlayLine(g2, ox, oy, iw, ih, scale, fi.bottomMinRow,
                    COL_DETECTED, lbl, false);
        }

        // Korrigiert (gruen) wenn Abweichung vom Ziel-Abstand
        int target = !Double.isNaN(overlayAvgDist)
                ? (int) Math.round(overlayAvgDist) : targetDistanceOrDefault();
        if (fi.topMinRow >= 0 && fi.bottomMinRow >= 0 && target > 0) {
            int delta = target - fi.distance;
            if (delta != 0) {
                // symmetrisch aufteilen: halbes Delta oben, Rest unten
                int addTop    = delta / 2;
                int addBottom = delta - addTop;
                int corrTop    = fi.topMinRow    - addTop;    // delta>0 => nach oben ruecken
                int corrBottom = fi.bottomMinRow + addBottom; // delta>0 => nach unten ruecken
                drawHOverlayLine(g2, ox, oy, iw, ih, scale, corrTop, COL_CORRECTED,
                        String.format("corr top=%d (%+d)", corrTop, -addTop), true);
                drawHOverlayLine(g2, ox, oy, iw, ih, scale, corrBottom, COL_CORRECTED,
                        String.format("corr bot=%d (%+d)  dist=%d",
                                corrBottom, addBottom, target), false);
            }
        }

        // Pilotloch (magenta)
        if (fi.holeTop >= 0) {
            drawHOverlayLine(g2, ox, oy, iw, ih, scale, fi.holeTop, COL_HOLE,
                    "holeTop=" + fi.holeTop, true);
        }
        if (fi.holeBottom >= 0) {
            String lbl = "holeBot=" + fi.holeBottom
                       + (fi.holeHeight > 0 ? "  h=" + fi.holeHeight : "");
            drawHOverlayLine(g2, ox, oy, iw, ih, scale, fi.holeBottom, COL_HOLE,
                    lbl, false);
        }


        // Lauf 2: horizontale Grenzen (nur innerhalb des jeweiligen Bandes zeichnen)
        // Oberes Band: zwischen topMinRow und holeTop, Farbe CYAN
        if (fi.topMinRow >= 0 && fi.holeTop >= 0) {
            if (fi.leftBorderUp >= 0) {
                drawVOverlayLine(g2, ox, oy, iw, ih, scale,
                        fi.leftBorderUp, fi.topMinRow, fi.holeTop,
                        COL_BORDER_UP, "L=" + fi.leftBorderUp, true);
            }
            if (fi.rightBorderUp >= 0) {
                drawVOverlayLine(g2, ox, oy, iw, ih, scale,
                        fi.rightBorderUp, fi.topMinRow, fi.holeTop,
                        COL_BORDER_UP, "R=" + fi.rightBorderUp, false);
            }
        }
        // Unteres Band: zwischen holeBottom und bottomMinRow, Farbe GELB
        if (fi.holeBottom >= 0 && fi.bottomMinRow >= 0) {
            if (fi.leftBorderDown >= 0) {
                drawVOverlayLine(g2, ox, oy, iw, ih, scale,
                        fi.leftBorderDown, fi.holeBottom, fi.bottomMinRow,
                        COL_BORDER_DN, "L=" + fi.leftBorderDown, true);
            }
            if (fi.rightBorderDown >= 0) {
                drawVOverlayLine(g2, ox, oy, iw, ih, scale,
                        fi.rightBorderDown, fi.holeBottom, fi.bottomMinRow,
                        COL_BORDER_DN, "R=" + fi.rightBorderDown, false);
            }
        }
        g2.setStroke(oldStroke);
    }

    /** Fallback-Zielabstand, wenn Lauf 1 noch nicht beendet ist. */
    private static int targetDistanceOrDefault() {
        return FrameAnalysisWindow.DEFAULT_TARGET_DISTANCE;
    }

    /**
     * Zeichnet eine horizontale Linie ueber die volle Bildbreite auf der Bild-Zeile
     * {@code row}, plus Beschriftung {@code label} am rechten Bildrand.
     * {@code labelAbove}: Text oberhalb (true) bzw. unterhalb (false) der Linie.
     */
    private void drawHOverlayLine(Graphics2D g2, int ox, int oy, int iw, int ih,
                                  double scale, int row, Color color,
                                  String label, boolean labelAbove) {
        int py = oy + (int) Math.round(row * scale);
        // volle Bildbreite
        g2.setColor(color);
        g2.drawLine(ox, py, ox + iw, py);

        // Beschriftung am rechten Bildrand
        FontMetrics fm = g2.getFontMetrics();
        int tw = fm.stringWidth(label);
        int th = fm.getAscent();
        int pad = 4;
        int tx = ox + iw - tw - 8;
        int ty;
        if (labelAbove) {
            ty = py - pad;
            if (ty - th < oy) ty = py + th + pad;              // wenn oben kein Platz -> unter Linie
        } else {
            ty = py + th + pad;
            if (ty > oy + ih) ty = py - pad;                    // wenn unten kein Platz -> ueber Linie
        }
        // Hintergrund
        g2.setColor(COL_BG_LABEL);
        g2.fillRoundRect(tx - 3, ty - th, tw + 6, th + 4, 6, 6);
        g2.setColor(color);
        g2.drawString(label, tx, ty);
    }

    /**
     * Zeichnet ein vertikales Liniensegment auf Bild-Spalte {@code col} zwischen den
     * Bild-Zeilen {@code rowFrom} und {@code rowTo} (jeweils inklusive), plus eine
     * kleine Beschriftung neben der Linie.
     * {@code labelLeft}: Beschriftung links (true) bzw. rechts (false) der Linie.
     */
    private void drawVOverlayLine(Graphics2D g2, int ox, int oy, int iw, int ih,
                                  double scale, int col, int rowFrom, int rowTo,
                                  Color color, String label, boolean labelLeft) {
        int px  = ox + (int) Math.round(col * scale);
        int py1 = oy + (int) Math.round(Math.min(rowFrom, rowTo) * scale);
        int py2 = oy + (int) Math.round(Math.max(rowFrom, rowTo) * scale);
        g2.setColor(color);
        g2.drawLine(px, py1, px, py2);

        // kleine Endpunkt-Marker, damit Segment auch bei sehr kurzen Baendern sichtbar bleibt
        g2.drawLine(px - 3, py1, px + 3, py1);
        g2.drawLine(px - 3, py2, px + 3, py2);

        // Beschriftung mittig auf Segment, seitlich neben der Linie
        FontMetrics fm = g2.getFontMetrics();
        int tw  = fm.stringWidth(label);
        int th  = fm.getAscent();
        int pad = 4;
        int ty  = (py1 + py2) / 2 + th / 2;
        int tx  = labelLeft ? (px - pad - tw) : (px + pad);
        // Ans Bild-Rechteck clippen
        if (tx < ox + 2)          tx = px + pad;
        if (tx + tw > ox + iw - 2) tx = px - pad - tw;

        g2.setColor(COL_BG_LABEL);
        g2.fillRoundRect(tx - 3, ty - th, tw + 6, th + 4, 6, 6);
        g2.setColor(color);
        g2.drawString(label, tx, ty);
    }

    // ------------------------------------------------ Helligkeits-Vektor-Plots

    /**
     * Plottet einen zeilenbasierten Helligkeitsvektor als vertikale Polylinie
     * (y = Zeile, x-Auslenkung = Helligkeit).
     *
     * @param baseX      Anker-X-Koordinate im Panel (Nullwert der Auslenkung)
     * @param oy         Panel-Y-Anker des Bildes
     * @param ih         Panel-Bildhoehe
     * @param scale      Skalierungsfaktor (Panel/Bild)
     * @param amplitude  positive Werte = Kurve nach RECHTS, negative = nach LINKS
     */
    private static void drawVerticalPlot(Graphics2D g2, float[] v,
            int baseX, int oy, int ih, double scale,
            int amplitude, Color color) {
        if (v == null || v.length < 2) return;
        double min = Float.POSITIVE_INFINITY, max = Float.NEGATIVE_INFINITY;
        for (float f : v) { if (f < min) min = f; if (f > max) max = f; }
        double range = max - min;
        if (range < 1e-6) return;

        // Basislinie (dezent) einzeichnen
        g2.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 60));
        g2.drawLine(baseX, oy, baseX, oy + ih);

        // Polylinie
        g2.setColor(color);
        int n  = v.length;
        int[] xs = new int[n];
        int[] ys = new int[n];
        for (int i = 0; i < n; i++) {
            double norm = (v[i] - min) / range; // 0..1
            xs[i] = baseX + (int) Math.round(norm * amplitude);
            ys[i] = oy + (int) Math.round((i * (double) ih) / n);
        }
        g2.drawPolyline(xs, ys, n);
    }

    /**
     * Plottet einen spaltenbasierten Helligkeitsvektor als horizontale Polylinie
     * (x = Spalte, y-Auslenkung = Helligkeit) zentriert um {@code centerRow}.
     */
    private static void drawHorizontalPlot(Graphics2D g2, float[] v,
                                           int ox, int iw, int oy, double scale,
                                           int centerRow, int amplitude, Color color) {
        if (v == null || v.length < 2) return;
        double min = Float.POSITIVE_INFINITY, max = Float.NEGATIVE_INFINITY;
        for (float f : v) { if (f < min) min = f; if (f > max) max = f; }
        double range = max - min;
        if (range < 1e-6) return;

        int baseY = oy + (int) Math.round(centerRow * scale);

        // Basislinie
        g2.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 60));
    	g2.drawLine(ox, baseY, ox + iw, baseY);

    	// Polylinie: Kurve zeigt bei hellen Werten NACH OBEN (subtract)
    	g2.setColor(color);
    	int n  = v.length;
    	int[] xs = new int[n];
    	int[] ys = new int[n];
    	double halfAmp = amplitude / 2.0;
    	for (int i = 0; i < n; i++) {
    		double norm = (v[i] - min) / range; // 0..1
    		xs[i] = ox + (int) Math.round((i * (double) iw) / n);
    		ys[i] = baseY - (int) Math.round((norm - 0.5) * 2.0 * halfAmp);
    	}
    	g2.drawPolyline(xs, ys, n);
    }

    /**
     * Setzt Overlay-Daten fuer den aktuell angezeigten Frame.
     * @param fi       FrameInfo (oder {@code null} um Overlay zu deaktivieren)
     * @param avgDist  gemittelter Ziel-Abstand top-bottom (Lauf 1);
     *                 {@link Double#NaN} deaktiviert die gruenen Korrektur-Linien
     */
    public void setFrameOverlay(FrameInfo fi, double avgDist) {
        this.overlayInfo    = fi;
        this.overlayAvgDist = avgDist;
        repaint();
    }

    public FrameInfo getFrameOverlay() { return overlayInfo; }

}
