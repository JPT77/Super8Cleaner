package de.jpt.super8;

import static org.bytedeco.opencv.global.opencv_imgproc.COLOR_BGR2GRAY;
import static org.bytedeco.opencv.global.opencv_imgproc.Canny;
import static org.bytedeco.opencv.global.opencv_imgproc.GaussianBlur;
import static org.bytedeco.opencv.global.opencv_imgproc.cvtColor;

import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.Size;

/**
 * Einfache Canny-Kantenerkennung: Graustufen -> Gaussian Blur -> Canny.
 * Erster Vertreter der spaeteren Filter-Kette.
 */
public final class EdgeFilter {

	/** Liefert ein 1-kanaliges Kantenbild. */
	public static Mat process(Mat src) {

		Mat gray = new Mat();
		Mat edges = new Mat();

		cvtColor(src, gray, COLOR_BGR2GRAY);
		GaussianBlur(gray, gray, new Size(5, 5), 1.4);
		Canny(gray, edges, 60, 120);

		gray.release();

		return edges;
	}

}
