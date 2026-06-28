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
import java.util.List;
import java.util.TreeSet;

import static org.bytedeco.opencv.global.opencv_imgproc.*;

/**
 * Super-8 Debug-Pipeline (Java/OpenCV/FFmpeg-Portierung).
 *
 * Funktionen:
 *  1. Inhaltspruefung VOR jeder Crop-Erkennung
 *  2. Vertikale Suche nur in den oberen 20 % / unteren 10 %
 *  3. Globaler Crop (horizontal + vertikal) ueber Median
 *  4. CSV-Ausgabe -> analysis.csv
 *  5. Szenenerkennung mit scene_id pro Frame
 *  6. Szenenexport als einzelne Dateien (eine pro Szene)
 *  7. FFV1 (lossless) in .mkv statt mp4v
 *  8. Debug-Preview-Video mit Overlay
 *  9. Weissabgleich pro Szene mit begrenzter Korrektur
 */
public class Super8Pipeline {

    private final Config cfg;
    private final List<Mat> frames = new ArrayList<Mat>();
    private final List<FrameRecord> records = new ArrayList<>();
    private double fps = 24.0;

    private int globalLeft, globalRight, globalTop, globalBottom;
    private final java.util.Map<Integer, double[]> sceneGains = new java.util.HashMap<>();
    private final TreeSet<Integer> sceneIds = new TreeSet<>();

    public Super8Pipeline(Config cfg) {
        this.cfg = cfg;
    }

    public void run() throws Exception {
        new File(cfg.scenesDir).mkdirs();
        fps = loadFrames(cfg.videoPath, frames);
        analyze();
        writeCsv();
        computeSceneGains();
        exportPreview();
        exportScenes();
        System.out.println("\nFertig. Alle Ausgaben unter: " + cfg.outputDir);
        frames.forEach(Mat::close);
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

    // 2) Analyse: Inhaltspruefung -> Crop -> Szenen-ID -> globaler Median-Crop
    private void analyze() {
        List<Integer> lefts = new ArrayList<>();
        List<Integer> rights = new ArrayList<>();
        List<Integer> tops = new ArrayList<>();
        List<Integer> bottoms = new ArrayList<>();

        Mat prevGray = null;
        int sceneId = -1;

        for (int i = 0; i < frames.size(); i++) {
        	System.out.println("Frame: "+i);
            Mat frame = frames.get(i);
            Mat gray = ImageOps.toGray(frame);

            FrameRecord rec = new FrameRecord();
            rec.frame = i;
            rec.contentScore = ImageOps.contentScore(gray);
            rec.valid = rec.contentScore > cfg.contentThreshold; // Inhaltspruefung VOR Crop

            if (rec.valid) {
                int[] hc = ImageOps.horizontalCrop(gray);
                int[] vc = ImageOps.verticalCrop(gray, cfg.topFrac, cfg.bottomFrac);
                rec.left = hc[0];
                rec.right = hc[1];
                rec.top = vc[0];
                rec.bottom = vc[1];
                lefts.add(hc[0]);
                rights.add(hc[1]);
                tops.add(vc[0]);
                bottoms.add(vc[1]);

                if (prevGray == null) {
                    rec.sceneScore = 0.0;
                    rec.sceneCut = true;
                    sceneId++;
                } else {
                    rec.sceneScore = ImageOps.sceneScore(prevGray, gray);
                    rec.sceneCut = rec.sceneScore > cfg.sceneThreshold;
                    if (rec.sceneCut) sceneId++;
                }
                rec.sceneId = sceneId;
                sceneIds.add(sceneId);

                if (prevGray != null) prevGray.close();
                prevGray = gray;
            } else {
                gray.close();
            }
            records.add(rec);
        }
        if (prevGray != null) prevGray.close();

        globalLeft = median(lefts);
        globalRight = median(rights);
        globalTop = median(tops);
        globalBottom = median(bottoms);

        System.out.printf("Globaler Crop  L,R,T,B : %d %d %d %d%n",
                globalLeft, globalRight, globalTop, globalBottom);
        System.out.println("Gueltige Frames        : " + lefts.size() + " / " + frames.size());
        System.out.println("Erkannte Szenen        : " + sceneIds.size());
    }

    // 4) CSV-Ausgabe
    private void writeCsv() throws IOException {
        try (FileWriter w = new FileWriter(cfg.csvPath)) {
            w.write(FrameRecord.csvHeader());
            w.write("\n");
            for (FrameRecord r : records) {
                w.write(r.toCsvRow());
                w.write("\n");
            }
        }
        System.out.println("geschrieben: " + cfg.csvPath);
    }

    // 9) Weissabgleich pro Szene (begrenzte Korrektur)
    private void computeSceneGains() {
        for (int sid : sceneIds) {
            double sumB = 0, sumG = 0, sumR = 0;
            int n = 0;
            for (FrameRecord r : records) {
                if (r.valid && r.sceneId == sid) {
                    double[] m = ImageOps.meanBGR(frames.get(r.frame));
                    sumB += m[0];
                    sumG += m[1];
                    sumR += m[2];
                    n++;
                }
            }
            double[] gains = ImageOps.computeWbGains(
                    new double[]{sumB / n, sumG / n, sumR / n}, cfg.wbMaxGain);
            sceneGains.put(sid, gains);
            System.out.printf("Szene %3d | %4d Frames | Gains B,G,R = [%.3f, %.3f, %.3f]%n",
                    sid, n, gains[0], gains[1], gains[2]);
        }
    }

    // 8) Debug-Preview mit Overlay (FFV1 lossless)
    private void exportPreview() throws Exception {
        if (frames.isEmpty()) return;
        int w = frames.get(0).cols();
        int h = frames.get(0).rows();
        try (FFmpegFrameRecorder rec = newRecorder(cfg.previewPath, w, h);
             OpenCVFrameConverter.ToMat conv = new OpenCVFrameConverter.ToMat()) {
            rec.start();
            Scalar green = new Scalar(0, 255, 0, 0);
            Scalar red = new Scalar(0, 0, 255, 0);
            Scalar yellow = new Scalar(0, 255, 255, 0);
            for (FrameRecord r : records) {
                Mat vis = frames.get(r.frame).clone();
                if (r.valid) {
                    rectangle(vis, new Point(globalLeft, globalTop),
                            new Point(globalRight, globalBottom), green, 2, LINE_8, 0);
                    putText(vis, "scene " + r.sceneId + "  score=" + round(r.contentScore, 2),
                            new Point(10, 30), FONT_HERSHEY_SIMPLEX, 0.7, yellow, 2, LINE_8, false);
                    if (r.sceneCut) {
                        putText(vis, "SCENE CUT", new Point(10, 65),
                                FONT_HERSHEY_SIMPLEX, 0.9, red, 2, LINE_8, false);
                    }
                } else {
                    putText(vis, "SKIP (kein Inhalt)", new Point(10, 30),
                            FONT_HERSHEY_SIMPLEX, 0.7, red, 2, LINE_8, false);
                }
                rec.record(conv.convert(vis));
                vis.close();
            }
            rec.stop();
        }
        System.out.println("geschrieben: " + cfg.previewPath);
    }

    // 6) Szenenexport als einzelne Dateien (FFV1 lossless), global gecroppt + Weissabgleich
    private void exportScenes() throws Exception {
        int cropW = globalRight - globalLeft;
        int cropH = globalBottom - globalTop;
        Rect roi = new Rect(globalLeft, globalTop, cropW, cropH);

        for (int sid : sceneIds) {
            String out = String.format("%s/scene_%03d.mkv", cfg.scenesDir, sid);
            int count = 0;
            try (FFmpegFrameRecorder rec = newRecorder(out, cropW, cropH);
                 OpenCVFrameConverter.ToMat conv = new OpenCVFrameConverter.ToMat()) {
                rec.start();
                double[] gains = sceneGains.get(sid);
                for (FrameRecord r : records) {
                    if (!r.valid || r.sceneId != sid) continue;
                    Mat crop = new Mat(frames.get(r.frame), roi).clone();
                    Mat wb = ImageOps.applyWb(crop, gains);
                    rec.record(conv.convert(wb));
                    crop.close();
                    wb.close();
                    count++;
                }
                rec.stop();
            }
            System.out.printf("Szene %3d -> %s  (%d Frames)%n", sid, out, count);
        }
    }

    // 7) Recorder mit FFV1 (lossless) im Matroska-Container
    private FFmpegFrameRecorder newRecorder(String path, int w, int h) {
        FFmpegFrameRecorder rec = new FFmpegFrameRecorder(path, w, h);
        rec.setVideoCodec(avcodec.AV_CODEC_ID_FFV1);
        rec.setFormat("matroska");
        rec.setPixelFormat(avutil.AV_PIX_FMT_BGR0); // verlustfreies RGB (kein Chroma-Subsampling)
        rec.setFrameRate(fps);
        return rec;
    }

    private static int median(List<Integer> xs) {
        if (xs.isEmpty()) return 0;
        List<Integer> s = new ArrayList<>(xs);
        s.sort(Integer::compareTo);
        int n = s.size();
        return (n % 2 == 1) ? s.get(n / 2)
                : (int) Math.floor((s.get(n / 2 - 1) + s.get(n / 2)) / 2.0);
    }

    private static String round(double v, int d) {
        double f = Math.pow(10, d);
        return String.valueOf(Math.round(v * f) / f);
    }
}
