package de.jpt.super8;

import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

/**
 * Einfache Canny-Kantenerkennung: Graustufen -> Gaussian Blur -> Canny.
 * Erster Vertreter der spaeteren Filter-Kette.
 */
public final class EdgeFilter {

    private EdgeFilter() {
    }

    /** Liefert ein 1-kanaliges Kantenbild. */
    public static Mat process(Mat src) {
        Mat gray = new Mat();
        Mat edges = new Mat();
        Imgproc.cvtColor(src, gray, Imgproc.COLOR_BGR2GRAY);
        Imgproc.GaussianBlur(gray, gray, new Size(5, 5), 1.4);
        Imgproc.Canny(gray, edges, 60, 120);
        gray.release();
        return edges;
    }
}
