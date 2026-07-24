package de.jpt.super8;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

import javax.swing.JPanel;

public class VideoPanel extends JPanel {

	private static final long serialVersionUID = 1L;

	private BufferedImage image;

	private boolean fitToWindow = false;

	public VideoPanel() {

		setBackground(Color.BLACK);

	}

	public void setImage(BufferedImage img) {

		image = img;

		if (img != null && !fitToWindow) {

			setPreferredSize(
					new Dimension(
							img.getWidth()/2,
							img.getHeight()/2));

			revalidate();

		}

		repaint();

	}

	public BufferedImage getImage() {

		return image;

	}

	public void setFitToWindow(boolean fit) {

		fitToWindow = fit;

		repaint();

	}

	public boolean isFitToWindow() {

		return fitToWindow;

	}

	@Override
	protected void paintComponent(Graphics g) {

		super.paintComponent(g);

		if (image == null)
			return;

		Graphics2D g2 = (Graphics2D) g;

		g2.setRenderingHint(
				RenderingHints.KEY_INTERPOLATION,
				RenderingHints.VALUE_INTERPOLATION_BILINEAR);

		if (!fitToWindow) {

			g2.drawImage(image, 0, 0, null);

			return;

		}

		int pw = getWidth();
		int ph = getHeight();

		int iw = image.getWidth();
		int ih = image.getHeight();

		double scale = Math.min(
				(double) pw / iw,
				(double) ph / ih);

		int w = (int) (iw * scale);
		int h = (int) (ih * scale);

		int x = (pw - w) / 2;
		int y = (ph - h) / 2;

		g2.drawImage(image, x, y, w, h, null);
	}
}