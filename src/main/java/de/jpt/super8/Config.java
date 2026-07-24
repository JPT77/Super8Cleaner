package de.jpt.super8;

import java.io.File;

/**
 * Zentrale, konfigurierbare Parameter der Super-8 Cleaner-Pipeline.
 * Pfade lassen sich per Programmargument oder Umgebungsvariable ueberschreiben.
 */
public final class Config {

    /** Pfad zum Quellvideo (Default: input.mp4). */
    public final String videoPath;
    /** Ausgabeordner. */
    public final String outputDir;
    /** Ordner fuer einzelne Szenendateien. */
    public final String scenesDir;
    /** CSV-Analyse. */
    public final String csvPath;
    /** Verlustfreie, gecroppte Ausgabe (FFV1 / .mkv). */
    public final String losslessPath;
    /** Lossy-Preview (.mp4) mit Schnittlinien + Helligkeitskurve. */
    public final String previewPath;

    // --- Parameter ---
    /** Mindest-Score, damit ein Frame als "nicht leer" gilt (Schritt 1). */
    public final double contentThreshold = 12.0;
    /** Vertikale Suche: obere 20 %. */
    public final double topFrac = 0.20;
    /** Vertikale Suche: untere 10 %. */
    public final double bottomFrac = 0.10;
    /** Erwartete Frame-Hoehe (px) – Zielabstand fuer die vertikale Crop-Suche. */
    public final int verticalFrameHeight;
    /** Maximale (begrenzte) Weissabgleich-Korrektur (Schritt 9). */
    public final double wbMaxGain = 1.25;

    // --- Diff-Klassifikation (Schritt 7), normierte mittlere Abweichung 0..1 ---
    /** Unterhalb dieser Schwelle gilt ein Frame als Dublette des Vorgaengers. */
    public final double duplicateThreshold = 0.012;
    /** Oberhalb dieser Schwelle gilt ein Frame als Szenenwechsel. */
    public final double sceneThreshold = 0.18;

    // --- Ausgabe ---
    /** Bitrate des Lossy-Previews in bit/s (gering). */
    public final int previewBitrate = 500_000;
    /** Wird die Framerate auf diesen Wert gerundet erkannt ... */
    public final double fpsDetect = 20.0;
    /** ... so wird sie beim Szenenexport hierauf korrigiert (nur Wert, Frames unveraendert). */
    public final double fpsCorrected = 18.0;

    public Config(String videoPath, String outputDir, int verticalFrameHeight) {
        this.videoPath = videoPath;
        this.outputDir = outputDir;
        this.verticalFrameHeight = verticalFrameHeight;
        this.scenesDir = outputDir + "/scenes";
        this.csvPath = outputDir + "/analysis.csv";
        String filename=new File(videoPath).getName();
        this.losslessPath = outputDir + "/"+filename+".cropped_lossless.mkv";
        this.previewPath = outputDir + "/"+filename+".preview_lossy.mp4";
    }

    /** Erzeugt Config aus Argumenten, faellt auf Umgebungsvariablen bzw. Defaults zurueck. */
    public static Config fromArgs(String[] args) {
        String video = args.length > 0 ? args[0] : envOr("SUPER8_INPUT", "input.mp4");
        String out = args.length > 1 ? args[1] : envOr("SUPER8_OUTPUT", "output");
        int frameH = args.length > 2 ? Integer.parseInt(args[2].trim())
                : Integer.parseInt(envOr("SUPER8_FRAME_HEIGHT", "920"));
        return new Config(video, out, frameH);
    }

    private static String envOr(String key, String def) {
        String v = System.getenv(key);
        return (v != null && !v.isEmpty()) ? v : def;
    }
}
