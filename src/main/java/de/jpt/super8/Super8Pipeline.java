package de.jpt.super8;

import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.OpenCVFrameConverter;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.Point;
import org.bytedeco.opencv.opencv_core.Rect;
import org.bytedeco.opencv.opencv_core.Scalar;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import static org.bytedeco.opencv.global.opencv_imgproc.*;

/**
 * Super-8 Cleaner-Pipeline (Java/OpenCV/FFmpeg).
 *
 * Ablauf (Schritte laut Anforderung):
 *  1. Leere Frames droppen (Inhalts-Score).
 *  2. Horizontale + vertikale Crop-Werte je Frame berechnen.
 *  3. Durchschnittlichen Abstand links/rechts bzw. oben/unten ermitteln.
 *  4. Crop-Werte auf diesen einheitlichen Abstand korrigieren und croppen.
 *  5. Verlustfreie Datei (FFV1) + Lossy-Preview (geringe Bitrate) mit
 *     Schnittlinien und Helligkeitskurve schreiben.
 *  6. Die neue verlustfreie Datei wieder einlesen.
 *  7. Frame-Diff berechnen -> Szenenwechsel / Normal / Dublette.
 *  8. Als einzelne Szenen speichern, Dubletten droppen, 20 fps -> 18 fps korrigieren.
 *  9. Farbkorrektur (begrenzter Weissabgleich pro Szene).
 * 10. Alle ermittelten Daten als CSV speichern.
 */
public class Super8Pipeline {

    private final Config cfg;
    private double fps = 24.0;

    private final List<Mat> originals = new ArrayList<>();
    private final List<FrameRecord> records = new ArrayList<>();   // alle Frames
    private final List<FrameRecord> kept = new ArrayList<>();      // nur behaltene (Reihenfolge = lossless)
    private final List<Mat> cropped = new ArrayList<>();           // re-gelesene, gecroppte Frames

    private int avgW, avgH;
    private final TreeSet<Integer> sceneIds = new TreeSet<>();
    private final Map<Integer, double[]> sceneGains = new LinkedHashMap<>();

    public Super8Pipeline(Config cfg) { this.cfg = cfg; }

    public void run() throws Exception {
        new File(cfg.scenesDir).mkdirs();

        fps = loadFrames(cfg.videoPath, originals); // Quellvideo laden
        analyzeAndDrop();             // Schritt 1 + 2
        computeUniformCrop();         // Schritt 3 + 4 (Werte)
        exportLossless();             // Schritt 4/5 (clean)
        originals.forEach(Mat::close); originals.clear();

        readLossless();               // Schritt 6
        classifyDiffs();              // Schritt 7
        exportPreview();              // Schritt 5 (lossy + Overlays)
        exportScenes();               // Schritt 8 + 9
        writeCsv();                   // Schritt 10

        cropped.forEach(Mat::close);
        System.out.println("\nFertig. Alle Ausgaben unter: " + cfg.outputDir);
    }

    /**
     * Converts all frames into a @List of @Mat, which probably is a 2D array (matrix)
     */
    private static double loadFrames(String filename, List<Mat> output) throws Exception {
        File f = new File(filename);
        if (!f.exists()) {
            throw new IOException("Video nicht gefunden: " + filename);
        }
        output.clear();
        double result = 0.0;
        try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(filename);
            OpenCVFrameConverter.ToMat conv = new OpenCVFrameConverter.ToMat()) {
            grabber.start();
            double r = grabber.getVideoFrameRate();
            if (r > 0) result = r;
            Frame fr;
            while ((fr = grabber.grabImage()) != null) {
                Mat m = conv.convert(fr);
				if (m != null) output.add(m.clone());
            }
            grabber.stop();
        }
        System.out.println("Frames: " + output.size() + " | FPS: " + result);
        return result;
    }

    // ---- Schritt 1 + 2 ----
    private void analyzeAndDrop() {
        for (int i = 0; i < originals.size(); i++) {
            Mat frame = originals.get(i);
            Mat gray = ImageOps.toGray(frame);

            FrameRecord rec = new FrameRecord();
            rec.frame = i;
            rec.contentScore = ImageOps.contentScore(gray);
            rec.brightness = ImageOps.brightness(gray);
            rec.kept = rec.contentScore > cfg.contentThreshold;

            if (rec.kept) {
                int[] vc = ImageOps.verticalCrop(gray, cfg.verticalFrameHeight);
                int[] hc = ImageOps.horizontalCrop(gray, vc[0], vc[1]); // aus oberem+unterem Drittel
                rec.rawTop = vc[0]; rec.rawBottom = vc[1];
                rec.rawLeft = hc[0]; rec.rawRight = hc[1];
                kept.add(rec);
            } else {
                rec.classification = "EMPTY";
            }
            gray.close();
            records.add(rec);
        }
        System.out.println("Behaltene Frames: " + kept.size() + " / " + originals.size());
    }

    // ---- Schritt 3 + 4 (Werte) ----
    private void computeUniformCrop() {
        if (kept.isEmpty()) throw new IllegalStateException("Keine nicht-leeren Frames gefunden.");
        double sumW = 0;
//		double sumH = 0;
        for (FrameRecord r : kept) {
			sumW += r.rawRight - r.rawLeft;
//			sumW += r.rawRight - r.rawLeft; 
//			sumH += r.rawBottom - r.rawTop; 7
		}
        int frameW = originals.get(kept.get(0).frame).cols();
        int frameH = originals.get(kept.get(0).frame).rows();
        avgW = Math.max(2, Math.min(frameW, (int) Math.round(sumW / kept.size())));
//        avgH = Math.max(2, Math.min(frameH, (int) Math.round(sumH / kept.size())));
        avgH = cfg.verticalFrameHeight;   // feste Crop-Hoehe = Zielwert

        for (FrameRecord r : kept) {
            int cx = (r.rawLeft + r.rawRight) / 2;
//            int cy = (r.rawTop + r.rawBottom) / 2;
//            r.cropX = clamp(cx - avgW / 2, 0, frameW - avgW);
//            r.cropY = clamp(cy - avgH / 2, 0, frameH - avgH);
//            r.cropW = avgW;
//            r.cropH = avgH;
            r.cropX = clamp(cx - avgW / 2, 0, frameW - avgW);
            r.cropY = clamp(r.rawTop, 0, Math.max(0, frameH - 1)); // ab erkannter Oberkante
            r.cropW = avgW;
            r.cropH = avgH;     // Unterkante (cropY+cropH) ggf. ausserhalb -> wird aufgefuellt
        }
//        System.out.printf("Einheitlicher Crop (Durchschnitt): %d x %d -> %d x %d%n", frameW, frameH, avgW, avgH);
        System.out.printf("Crop: %d x %d (feste Hoehe ab Oberkante, Ueberstand wird aufgefuellt)%n", avgW, avgH);
    }

    // ---- Schritt 4/5: verlustfreie, gecroppte Ausgabe ----
    private void exportLossless() throws Exception {
        try (FFmpegFrameRecorder rec = newLossless(cfg.losslessPath, avgW, avgH, fps);
             OpenCVFrameConverter.ToMat conv = new OpenCVFrameConverter.ToMat()) {
            rec.start();
            for (int i = 0; i < kept.size(); i++) {
                Mat crop = assembleCrop(i);
//            for (FrameRecord r : kept) {
//                Mat crop = new Mat(originals.get(r.frame), new Rect(r.cropX, r.cropY, r.cropW, r.cropH)).clone();
                rec.record(conv.convert(crop));
                crop.close();
            }
            rec.stop();
        }
        System.out.println("geschrieben (lossless): " + cfg.losslessPath);
    }

    /**
     * Baut den (festen) Crop fuer einen behaltenen Frame ab der Oberkante.
     * Ragt die Unterkante aus dem Bild, werden die fehlenden unteren Zeilen aus den
     * OBEREN Zeilen des vorherigen Frames aufgefuellt; gibt es keinen, bleibt es schwarz.
     */
    private Mat assembleCrop(int keptIdx) {
        FrameRecord r = kept.get(keptIdx);
        Mat cur = originals.get(r.frame);
        int H = cur.rows(), W = cur.cols();
        Mat dst = new Mat(r.cropH, r.cropW, cur.type(), Scalar.all(0)); // Default: schwarz

        int x = clamp(r.cropX, 0, Math.max(0, W - 1));
        int y = clamp(r.cropY, 0, Math.max(0, H - 1));
        int availBottom = Math.min(r.cropH, H - y);
        copyRegion(cur, x, y, r.cropW, availBottom, dst, 0, 0);

        int missing = r.cropH - availBottom;
        if (missing > 0 && keptIdx > 0) {       // Ueberstand aus oberen Zeilen des vorherigen Frames
            Mat prev = originals.get(kept.get(keptIdx - 1).frame);
            int take = Math.min(missing, prev.rows());
            copyRegion(prev, x, 0, r.cropW, take, dst, 0, availBottom);
        }
        return dst;
    }

    /** Kopiert eine Quellregion in das Ziel; clippt sicher gegen beide Mat-Dimensionen. */
    private static void copyRegion(Mat src, int sx, int sy, int w, int h, Mat dst, int dx, int dy) {
        w = Math.min(w, Math.min(src.cols() - sx, dst.cols() - dx));
        h = Math.min(h, Math.min(src.rows() - sy, dst.rows() - dy));
        if (sx < 0 || sy < 0 || dx < 0 || dy < 0 || w <= 0 || h <= 0) return;
        Mat s = new Mat(src, new Rect(sx, sy, w, h));
        Mat d = new Mat(dst, new Rect(dx, dy, w, h));
        s.copyTo(d);
        s.close(); d.close();
    }

    // ---- Schritt 5: Lossy-Preview mit Schnittlinien + Helligkeitskurve ----
    private void exportPreview() throws Exception {
        int W = originals.get(0).cols(), H = originals.get(0).rows();
        int pw = W - (W % 2), ph = H - (H % 2);                  // gerade Dimensionen fuer H.264
        Rect previewRoi = new Rect(0, 0, pw, ph);
        int band = Math.min(80, H / 4);                          // Hoehe der waagrechten Kurve
        int bandV = Math.min(80, W / 4);                          // Breite der senkrechten Kurve
        Scalar green = new Scalar(0, 255, 0, 0);
        Scalar orange = new Scalar(0, 165, 255, 0);              // gemessene Framegrenzen
        Scalar cyan = new Scalar(255, 255, 0, 0);                 // waagrechte Helligkeitskurve (unten)
        Scalar magenta = new Scalar(255, 0, 255, 0);              // senkrechte Helligkeitskurve (links)
        Scalar red = new Scalar(0, 0, 255, 0);

        try (FFmpegFrameRecorder rec = newPreview(cfg.previewPath, pw, ph, fps);
             OpenCVFrameConverter.ToMat conv = new OpenCVFrameConverter.ToMat()) {
            rec.start();
            for (FrameRecord r : records) {
                Mat vis = originals.get(r.frame).clone();
                Mat gray = ImageOps.toGray(vis);
                // Waagrechte Helligkeitskurve (Spaltenmittel) am unteren Rand
                double[] cm = ImageOps.columnMeans(gray);
                for (int x = 1; x < cm.length; x++) {
                    int y0 = (int) (H - 1 - cm[x - 1] / 255.0 * band);
                    int y1 = (int) (H - 1 - cm[x] / 255.0 * band);
                    line(vis, new Point(x - 1, y0), new Point(x, y1), cyan, 1, LINE_8, 0);
                }
                // Senkrechte Helligkeitskurve (Zeilenmittel) am linken Rand
                double[] rm = ImageOps.rowMeans(gray);
                for (int y = 1; y < rm.length; y++) {
                    int x0 = (int) (rm[y - 1] / 255.0 * bandV);
                    int x1 = (int) (rm[y] / 255.0 * bandV);
                    line(vis, new Point(x0, y - 1), new Point(x1, y), magenta, 1, LINE_8, 0);
                }
                if (r.kept) {
                    // Tatsaechlich gemessene Framegrenzen (vertikaler + horizontaler Crop), orange
                    line(vis, new Point(0, r.rawTop), new Point(W - 1, r.rawTop), orange, 1, LINE_8, 0);
                    line(vis, new Point(0, r.rawBottom), new Point(W - 1, r.rawBottom), orange, 1, LINE_8, 0);
                    line(vis, new Point(r.rawLeft, 0), new Point(r.rawLeft, H - 1), orange, 1, LINE_8, 0);
                    line(vis, new Point(r.rawRight, 0), new Point(r.rawRight, H - 1), orange, 1, LINE_8, 0);
                    // Korrigierter, einheitlicher Crop (Schnittlinien), gruen
                    rectangle(vis, new Point(r.cropX, r.cropY),
                            new Point(r.cropX + r.cropW, r.cropY + r.cropH), green, 2, LINE_8, 0);
                    putText(vis, "f" + r.frame + "  br=" + Math.round(r.brightness),
                            new Point(10, 25), FONT_HERSHEY_SIMPLEX, 0.6, green, 2, LINE_8, false);
                } else {
                    putText(vis, "f" + r.frame + "  EMPTY (dropped)", new Point(10, 25),
                            FONT_HERSHEY_SIMPLEX, 0.6, red, 2, LINE_8, false);
                    
                    putText(vis,
                            "f" + r.frame +
//                            "  Chapter " + chapter +
                            "  br=" + Math.round(r.brightness),
                            new Point(10, 25),
                            FONT_HERSHEY_SIMPLEX,
                            0.6,
                            green,
                            2,
                            LINE_8,
                            false);
                }
                gray.close();
                Mat outFrame = (pw == W && ph == H) ? vis : new Mat(vis, previewRoi).clone();
                rec.record(conv.convert(outFrame));
                if (outFrame != vis) outFrame.close();
                vis.close();
            }
            rec.stop();
        }
        System.out.println("geschrieben (lossy preview): " + cfg.previewPath);
    }

    // ---- Schritt 6: verlustfreie Datei wieder einlesen ----
    private void readLossless() throws Exception {
        try (FFmpegFrameGrabber g = new FFmpegFrameGrabber(cfg.losslessPath);
             OpenCVFrameConverter.ToMat conv = new OpenCVFrameConverter.ToMat()) {
            g.start();
            Frame fr;
            while ((fr = g.grabImage()) != null) {
                Mat m = conv.convert(fr);
                if (m != null) cropped.add(m.clone());
            }
            g.stop();
        }
        System.out.println("eingelesen (lossless): " + cropped.size() + " Frames");
    }

    // ---- Schritt 7: Diff -> SCENE / NORMAL / DUP ----
    private void classifyDiffs() {
        Mat prev = null;
        int sceneId = -1;
        int n = Math.min(kept.size(), cropped.size());
        for (int i = 0; i < n; i++) {
            FrameRecord r = kept.get(i);
            Mat cur = cropped.get(i);
            if (prev == null) {
                r.diffPrev = 0.0; r.classification = "SCENE"; sceneId = 0;
            } else {
                double d = ImageOps.frameDiff(prev, cur);
                r.diffPrev = d;
                if (d < cfg.duplicateThreshold) {
                    r.classification = "DUP";
                } else if (d > cfg.sceneThreshold) {
                    r.classification = "SCENE"; sceneId++;
                } else {
                    r.classification = "NORMAL";
                }
            }
            r.sceneId = sceneId;
            sceneIds.add(sceneId);
            prev = cur;
        }
        long dup = kept.stream().filter(r -> "DUP".equals(r.classification)).count();
        System.out.printf("Szenen: %d | Dubletten: %d%n", sceneIds.size(), dup);
    }

    // ---- Schritt 8 + 9: Szenenexport, Dubletten droppen, Farbkorrektur, fps-Korrektur ----
    private void exportScenes() throws Exception {
        double outFps = (Math.round(fps) == (long) cfg.fpsDetect) ? cfg.fpsCorrected : fps;
        if (outFps != fps)
            System.out.printf("Framerate korrigiert: %.0f -> %.0f fps%n", fps, outFps);

        // Farbkorrektur-Gains pro Szene (nur Nicht-Dubletten)
        for (int sid : sceneIds) {
            double sb = 0, sg = 0, sr = 0; int c = 0;
            for (int i = 0; i < kept.size(); i++) {
                FrameRecord r = kept.get(i);
                if (r.sceneId == sid && !"DUP".equals(r.classification)) {
                    double[] m = ImageOps.meanBGR(cropped.get(i));
                    sb += m[0]; sg += m[1]; sr += m[2]; c++;
                }
            }
            double[] gains = (c == 0) ? new double[]{1, 1, 1}
                    : ImageOps.computeWbGains(new double[]{sb / c, sg / c, sr / c}, cfg.wbMaxGain);
            sceneGains.put(sid, gains);
        }

        for (int sid : sceneIds) {
        	int firstframe = -1;
        	int lastframe = -1;
            double[] gains = sceneGains.get(sid);
            String out = String.format("%s/scene_%03d.mkv", cfg.scenesDir, sid);
            int written = 0;
            try (FFmpegFrameRecorder rec = newLossless(out, avgW, avgH, outFps);
                 OpenCVFrameConverter.ToMat conv = new OpenCVFrameConverter.ToMat()) {
                rec.start();
                for (int i = 0; i < kept.size(); i++) {
                    FrameRecord r = kept.get(i);
                    if (r.sceneId != sid || "DUP".equals(r.classification)) continue;
                    if (firstframe < 0) firstframe = r.frame;
                    lastframe = r.frame;
                    r.gainB = gains[0]; r.gainG = gains[1]; r.gainR = gains[2];
                    Mat wb = ImageOps.applyWb(cropped.get(i), gains);
                    rec.record(conv.convert(wb));
                    wb.close();
                    written++;
                }
                rec.stop();
            }
            System.out.printf("Szene %3d -> %s %d-%d (%d Frames @ %.0f fps)%n", sid, out, firstframe, lastframe, written, outFps);
        }
    }

    // ---- Schritt 10: CSV ----
    private void writeCsv() throws IOException {
        try (FileWriter w = new FileWriter(cfg.csvPath)) {
            w.write(FrameRecord.csvHeader()); w.write("\n");
            for (FrameRecord r : records) { w.write(r.toCsvRow()); w.write("\n"); }
        }
        System.out.println("geschrieben (csv): " + cfg.csvPath);
    }

    // ---- Recorder-Helfer ----
    private FFmpegFrameRecorder newLossless(String path, int w, int h, double frameRate) {
        FFmpegFrameRecorder rec = new FFmpegFrameRecorder(path, w, h);
        rec.setVideoCodec(avcodec.AV_CODEC_ID_FFV1);
        rec.setFormat("matroska");
        rec.setPixelFormat(avutil.AV_PIX_FMT_BGR0);   // verlustfreies RGB
        rec.setFrameRate(frameRate);
        return rec;
    }

    private FFmpegFrameRecorder newPreview(String path, int w, int h, double frameRate) {
        FFmpegFrameRecorder rec = new FFmpegFrameRecorder(path, w, h);
        rec.setVideoCodec(avcodec.AV_CODEC_ID_MPEG4); // ueberall verfuegbar, geringe Datenrate
        rec.setFormat("mp4");
        rec.setPixelFormat(avutil.AV_PIX_FMT_YUV420P);
        rec.setVideoBitrate(cfg.previewBitrate);
        rec.setFrameRate(frameRate);
        return rec;
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
