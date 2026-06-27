package de.jpt.super8;

import org.bytedeco.javacpp.FloatPointer;
import org.bytedeco.javacpp.IntPointer;
import org.bytedeco.javacpp.indexer.DoubleIndexer;
import org.bytedeco.javacpp.indexer.FloatIndexer;
import org.bytedeco.javacpp.indexer.UByteIndexer;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.MatVector;
import org.bytedeco.opencv.opencv_core.Scalar;
import org.bytedeco.opencv.opencv_core.Size;

import java.util.Arrays;

import static org.bytedeco.opencv.global.opencv_core.*;
import static org.bytedeco.opencv.global.opencv_imgproc.*;

/**
 * Bildverarbeitungs-Hilfsfunktionen (1:1-Portierung der Python/OpenCV-Pipeline).
 */
public final class ImageOps {

    private ImageOps() {}

    /** Graustufenbild aus BGR. Ruft der Aufrufer auf; Rueckgabe muss freigegeben werden. */
    public static Mat toGray(Mat bgr) {
        Mat gray = new Mat();
        cvtColor(bgr, gray, COLOR_BGR2GRAY);
        return gray;
    }

    /** Kombinierter Inhalts-Score: Kontrast + Kantenenergie + dynamische Range. */
    public static double contentScore(Mat gray) {
        Mat meanM = new Mat();
        Mat stdM = new Mat();
        meanStdDev(gray, meanM, stdM);
        DoubleIndexer si = stdM.createIndexer();
        double std = si.get(0);
        si.release();

        Mat edges = new Mat();
        Canny(gray, edges, 50, 150);
        double edgeEnergy = mean(edges).get(0);

        double[] pr = percentilePair8u(gray, 10, 90);
        double dynamicRange = pr[1] - pr[0];

        meanM.close();
        stdM.close();
        edges.close();
        return 0.5 * std + 0.3 * edgeEnergy + 0.2 * dynamicRange;
    }

    /** Inhaltspruefung – wird VOR jeder Crop-Erkennung aufgerufen. */
    public static boolean isValidFrame(Mat gray, double threshold) {
        return contentScore(gray) > threshold;
    }

    /** Geglaettetes vertikales Profil (Zeilenmittelwerte). */
    public static double[] verticalProfile(Mat gray) {
        Mat prof = new Mat();
        reduce(gray, prof, 1, REDUCE_AVG, CV_32F); // (rows x 1)
        GaussianBlur(prof, prof, new Size(1, 51), 0);
        int rows = prof.rows();
        double[] out = new double[rows];
        FloatIndexer fi = prof.createIndexer();
        for (int i = 0; i < rows; i++) {
            out[i] = fi.get(i, 0);
        }
        fi.release();
        prof.close();
        return out;
    }

    /**
     * Vertikaler Crop: oberer Rand nur in den oberen {@code topFrac},
     * unterer Rand nur in den unteren {@code bottomFrac} des Bildes.
     */
    public static int[] verticalCrop(Mat gray, double topFrac, double bottomFrac) {
        int h = gray.rows();
        double[] prof = verticalProfile(gray);
        double thr = percentile(prof, 25);
        int topRegion = Math.max(1, (int) (topFrac * h));
        int bottomRegion = h - Math.max(1, (int) (bottomFrac * h));

        int top = 0;
        for (int i = topRegion - 1; i >= 0; i--) {
            if (prof[i] < thr) { top = i + 1; break; }
        }
        int bottom = h;
        for (int i = bottomRegion; i < h; i++) {
            if (prof[i] < thr) { bottom = i; break; }
        }
        if (bottom <= top) { top = 0; bottom = h; }
        return new int[]{top, bottom};
    }

    /** Horizontaler Crop ueber Kantenenergie pro Spalte. */
    public static int[] horizontalCrop(Mat gray) {
        Mat edges = new Mat();
        Canny(gray, edges, 50, 150);
        Mat col = new Mat();
        reduce(edges, col, 0, REDUCE_SUM, CV_32F); // (1 x cols)
        int cols = col.cols();
        double[] vals = new double[cols];
        FloatIndexer fi = col.createIndexer();
        for (int j = 0; j < cols; j++) {
            vals[j] = fi.get(0, j);
        }
        fi.release();
        edges.close();
        col.close();

        double t = percentile(vals, 30);
        int first = -1, last = -1;
        for (int j = 0; j < cols; j++) {
            if (vals[j] > t) {
                if (first < 0) first = j;
                last = j;
            }
        }
        if (first < 0) return new int[]{0, gray.cols()};
        return new int[]{first, last};
    }

    /** Szenen-Distanz (Bhattacharyya) zwischen zwei Graustufen-Histogrammen. */
    public static double sceneScore(Mat g1, Mat g2) {
        Mat h1 = hist64(g1);
        Mat h2 = hist64(g2);
        double d = compareHist(h1, h2, HISTCMP_BHATTACHARYYA);
        h1.close();
        h2.close();
        return d;
    }

    private static Mat hist64(Mat gray) {
        Mat hist = new Mat();
        calcHist(new MatVector(gray), new IntPointer(new int[]{0}), new Mat(), hist,
                new IntPointer(new int[]{64}), new FloatPointer(new float[]{0f, 256f}), false);
        normalize(hist, hist, 1.0, 0.0, NORM_L2, -1, new Mat());
        return hist;
    }

    /** Mittelwert je Kanal (B, G, R). */
    public static double[] meanBGR(Mat bgr) {
        Scalar s = mean(bgr);
        return new double[]{s.get(0), s.get(1), s.get(2)};
    }

    /** Begrenzte Weissabgleich-Gains aus Mittelwert (statt ungebremstem Gray-World). */
    public static double[] computeWbGains(double[] meanBGR, double maxGain) {
        double mb = meanBGR[0], mg = meanBGR[1], mr = meanBGR[2];
        double m = (mb + mg + mr) / 3.0;
        double[] g = {m / (mb + 1e-6), m / (mg + 1e-6), m / (mr + 1e-6)};
        for (int i = 0; i < 3; i++) {
            g[i] = Math.max(1.0 / maxGain, Math.min(maxGain, g[i]));
        }
        return g;
    }

    /** Wendet Weissabgleich-Gains kanalweise an (mit Saturierung 0..255). */
    public static Mat applyWb(Mat bgr, double[] gains) {
        MatVector ch = new MatVector();
        split(bgr, ch);
        for (int i = 0; i < 3; i++) {
            Mat c = ch.get(i);
            c.convertTo(c, -1, gains[i], 0); // dst = src*gain, saturate_cast
        }
        Mat out = new Mat();
        merge(ch, out);
        ch.close();
        return out;
    }

    // ----------------- Statistik-Helfer -----------------

    /** Lineares (numpy-aehnliches) Perzentil ueber ein double-Array. */
    public static double percentile(double[] a, double q) {
        double[] b = a.clone();
        Arrays.sort(b);
        if (b.length == 1) return b[0];
        double idx = q / 100.0 * (b.length - 1);
        int lo = (int) Math.floor(idx);
        int hi = (int) Math.ceil(idx);
        double frac = idx - lo;
        return b[lo] * (1 - frac) + b[hi] * frac;
    }

    /** Effizientes Perzentilpaar fuer 8-Bit-Graustufen via 256-Bin-Histogramm. */
    private static double[] percentilePair8u(Mat gray, double qLow, double qHigh) {
        long[] hist = new long[256];
        UByteIndexer ui = gray.createIndexer();
        int rows = gray.rows();
        int cols = gray.cols();
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                hist[ui.get(r, c) & 0xFF]++;
            }
        }
        ui.release();
        long total = (long) rows * cols;
        return new double[]{histPercentile(hist, total, qLow), histPercentile(hist, total, qHigh)};
    }

    private static double histPercentile(long[] hist, long total, double q) {
        long target = (long) Math.ceil(q / 100.0 * total);
        if (target < 1) target = 1;
        long cum = 0;
        for (int v = 0; v < 256; v++) {
            cum += hist[v];
            if (cum >= target) return v;
        }
        return 255;
    }
}
