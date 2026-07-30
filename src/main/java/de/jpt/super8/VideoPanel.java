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

public class VideoPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    private BufferedImage image;

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

}
