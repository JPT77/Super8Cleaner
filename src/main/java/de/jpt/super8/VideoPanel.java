package de.jpt.super8;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;

import javax.swing.JPanel;
import javax.swing.Scrollable;

import org.opencv.core.CvType;
import org.opencv.core.Mat;

/**
 * Zeigt einen Videoframe an. Zwei Modi:
 *  - ORIGINAL: unskaliert (1:1), bei Bedarf mit Scrollbalken.
 *  - FIT: an das Fenster angepasst (seitenverhaeltnistreu).
 * Umschalten per Doppelklick. Enthaelt keinerlei OpenCV-Bezug.
 */
public class VideoPanel extends JPanel implements Scrollable {

    private static final long serialVersionUID = 1L;

    private BufferedImage image;
    private boolean fitToWindow = false;

    public VideoPanel() {
        setBackground(Color.BLACK);
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    toggleFit();
                }
            }
        });
    }

    public void setImage(BufferedImage img) {
        image = img;
        if (img != null && !fitToWindow) {
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

    public boolean isFitToWindow() {
        return fitToWindow;
    }

    public void setFitToWindow(boolean fit) {
        fitToWindow = fit;
        if (!fit && image != null) {
            setPreferredSize(new Dimension(image.getWidth(), image.getHeight()));
        }
        revalidate();
        repaint();
    }

    public void toggleFit() {
        setFitToWindow(!fitToWindow);
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

        if (!fitToWindow) {
            g2.drawImage(image, 0, 0, null);   // 1:1, unskaliert
            return;
        }
        int pw = getWidth();
        int ph = getHeight();
        int iw = image.getWidth();
        int ih = image.getHeight();
        double scale = Math.min((double) pw / iw, (double) ph / ih);
        int w = (int) (iw * scale);
        int h = (int) (ih * scale);
        int x = (pw - w) / 2;
        int y = (ph - h) / 2;
        g2.drawImage(image, x, y, w, h, null);
    }

    // --- Scrollable: im FIT-Modus die Viewport-Groesse uebernehmen ---

    @Override
    public Dimension getPreferredScrollableViewportSize() {
        return getPreferredSize();
    }

    @Override
    public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
        return 16;
    }

    @Override
    public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
        return 100;
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
        return fitToWindow;
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
        return fitToWindow;
    }
}
