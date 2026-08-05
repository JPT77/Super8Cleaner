package de.jpt.super8;

import org.bytedeco.javacpp.FloatPointer;
import org.bytedeco.javacpp.IntPointer;
import org.bytedeco.javacpp.indexer.DoubleIndexer;
import org.bytedeco.javacpp.indexer.FloatIndexer;
import org.bytedeco.javacpp.indexer.UByteIndexer;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.MatVector;
import org.bytedeco.opencv.opencv_core.Rect;
import org.bytedeco.opencv.opencv_core.Scalar;
import org.bytedeco.opencv.opencv_core.Size;

import java.util.Arrays;

import static org.bytedeco.opencv.global.opencv_core.*;
import static org.bytedeco.opencv.global.opencv_imgproc.*;

/** Bildverarbeitungs-Hilfsfunktionen (OpenCV). */
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
//        System.out.printf("0.5*std+0.3*edgeEnergy+0.2*dynamicRange="
//        		+ "0.5*%f+0.3*%f+0.2*%f="
//        		+ "%f+%f+%f="
//        		+ "%f\n",
//        		std, edgeEnergy, dynamicRange,
//        		0.5*std,0.3*edgeEnergy,0.2*dynamicRange,
//        		0.5*std+0.3*edgeEnergy+0.2*dynamicRange
//        );
        return 0.5 * std + 0.3 * edgeEnergy + 0.2 * dynamicRange;
    }

    /** Mittlere Helligkeit (0..255). */
    public static double brightness(Mat gray) {
        return mean(gray).get(0);
    }

    private static boolean isSameFrame(Mat currentFrame, Mat lastFrame, double threshold) {
    	return false;
    }

    /** Geglaettetes vertikales Profil (Zeilenmittelwerte). */
    public static double[] verticalProfile(Mat gray) {
        Mat prof = new Mat();
        reduce(gray, prof, 1, REDUCE_AVG, CV_32F); // (rows x 1)
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

    /** Spaltenmittelwerte (Helligkeit pro Spalte) – fuer die waagrechte Helligkeitskurve. */
    public static double[] columnMeans(Mat gray) {
        Mat col = new Mat();
        reduce(gray, col, 0, REDUCE_AVG, CV_32F); // (1 x cols)
        int cols = col.cols();
        double[] out = new double[cols];
        FloatIndexer fi = col.createIndexer();
        for (int j = 0; j < cols; j++) out[j] = fi.get(0, j);
        fi.release(); col.close();
        return out;
    }

    /** Zeilenmittelwerte (Helligkeit pro Zeile) – fuer die senkrechte Helligkeitskurve. */
    public static double[] rowMeans(Mat gray) {
        Mat row = new Mat();
        reduce(gray, row, 1, REDUCE_AVG, CV_32F); // (rows x 1)
        int rows = row.rows();
        double[] out = new double[rows];
        FloatIndexer fi = row.createIndexer();
        for (int i = 0; i < rows; i++) out[i] = fi.get(i, 0);
        fi.release(); row.close();
        return out;
    }

    /**
     * Vertikaler Crop: oberer Rand nur in den oberen {@code topFrac},
     * unterer Rand nur in den unteren {@code bottomFrac} des Bildes.
     */
    public static int[] verticalCrop(Mat gray, int targetDist) {
        int h = gray.rows();
        double[] prof = verticalProfile(gray);

        // 1) alle strikten lokalen Minima
        java.util.List<Integer> minima = new java.util.ArrayList<>();
        for (int i = 1; i < h - 1; i++) {
            if (prof[i] < prof[i - 1] && prof[i] <= prof[i + 1]) minima.add(i);
        }
        if (minima.size() < 2) return new int[]{0, h};

        // 2) die 5 staerksten (kleinster Wert) mit Mindestabstand auswaehlen
        minima.sort((a, b) -> Double.compare(prof[a], prof[b]));
        int minSep = Math.max(1, targetDist / 4);
        java.util.List<Integer> picked = new java.util.ArrayList<>();
        for (int idx : minima) {
            boolean ok = true;
            for (int p : picked) if (Math.abs(p - idx) < minSep) { ok = false; break; }
            if (ok) picked.add(idx);
            if (picked.size() == 5) break;
        }
        if (picked.size() < 2) picked = new java.util.ArrayList<>(minima.subList(0, Math.min(5, minima.size())));

        // 3) Paar mit Abstand am naechsten zu targetDist
        int bestTop = 0, bestBottom = h;
        double bestErr = Double.MAX_VALUE;
        for (int i = 0; i < picked.size(); i++) {
            for (int j = i + 1; j < picked.size(); j++) {
                int a = Math.min(picked.get(i), picked.get(j));
                int b = Math.max(picked.get(i), picked.get(j));
                double err = Math.abs((b - a) - targetDist);
                if (err < bestErr) { bestErr = err; bestTop = a; bestBottom = b; }
            }
        }
        return new int[]{bestTop, bestBottom};
    }

    /**
     * Horizontaler Crop aus dem OBEREN und UNTEREN Drittel des vertikal gecroppten
     * Bereichs [top, bottom). Dort liegen die seitlichen Bildkanten/Stege am klarsten;
     * das mittlere Drittel (Bildinhalt) wird ignoriert.
     */
    public static int[] horizontalCrop(Mat gray, int top, int bottom) {
        int h = gray.rows(), w = gray.cols();
        top = Math.max(0, Math.min(top, h - 1));
        bottom = Math.max(top + 1, Math.min(bottom, h));
        int third = Math.max(1, (bottom - top) / 3);

        double[] colEnergy = new double[w];
        addBandEdges(gray, top, top + third, colEnergy);            // oberes Drittel
        addBandEdges(gray, bottom - third, bottom, colEnergy);      // unteres Drittel

        double t = percentile(colEnergy, 30);
        int first = -1, last = -1;
        for (int j = 0; j < w; j++) if (colEnergy[j] > t) { if (first < 0) first = j; last = j; }
        if (first < 0) return new int[]{0, w};
        return new int[]{first, last};
    }

    /** Addiert die Kantenenergie pro Spalte fuer das Zeilenband [y0, y1) auf colEnergy. */
    private static void addBandEdges(Mat gray, int y0, int y1, double[] colEnergy) {
        y0 = Math.max(0, y0);
        y1 = Math.min(gray.rows(), y1);
        if (y1 <= y0) return;
        Mat band = new Mat(gray, new Rect(0, y0, gray.cols(), y1 - y0));
        Mat edges = new Mat();
        Canny(band, edges, 50, 150);
        Mat col = new Mat();
        reduce(edges, col, 0, REDUCE_SUM, CV_32F);
        FloatIndexer fi = col.createIndexer();
        for (int j = 0; j < gray.cols(); j++) colEnergy[j] += fi.get(0, j);
        fi.release(); col.close(); edges.close(); band.close();
    }

    /** Normierte mittlere Abweichung zweier (gleich grosser) BGR-Frames, 0..1. */
    public static double frameDiff(Mat a, Mat b) {
        Mat ga = toGray(a), gb = toGray(b), d = new Mat();
        absdiff(ga, gb, d);
        double m = mean(d).get(0) / 255.0;
        ga.close(); gb.close(); d.close();
        return m;
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
