package de.jpt.super8;

/**
 * Zentrale, konfigurierbare Parameter der Super-8 Debug-Pipeline.
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
    /** Debug-Preview-Video (FFV1 / .mkv). */
    public final String previewPath;

    // --- Parameter ---
    /** Mindest-Score fuer "Frame hat Inhalt". */
    public final double contentThreshold = 12.0;
    /** Bhattacharyya-Distanz fuer Szenenschnitt. */
    public final double sceneThreshold = 0.5;
    /** Vertikale Suche: obere 20 %. */
    public final double topFrac = 0.20;
    /** Vertikale Suche: untere 10 %. */
    public final double bottomFrac = 0.10;
    /** Maximale (begrenzte) Weissabgleich-Korrektur. */
    public final double wbMaxGain = 1.25;

    public Config(String videoPath, String outputDir) {
        this.videoPath = videoPath;
        this.outputDir = outputDir;
        this.scenesDir = outputDir + "/scenes";
        this.csvPath = outputDir + "/analysis.csv";
        this.previewPath = outputDir + "/debug_preview.mkv";
    }

    /** Erzeugt Config aus Argumenten, faellt auf Umgebungsvariablen bzw. Defaults zurueck. */
    public static Config fromArgs(String[] args) {
        String video = args.length > 0 ? args[0]
                : envOr("SUPER8_INPUT", "input.mp4");
        String out = args.length > 1 ? args[1]
                : envOr("SUPER8_OUTPUT", "output");
        return new Config(video, out);
    }

    private static String envOr(String key, String def) {
        String v = System.getenv(key);
        return (v != null && !v.isEmpty()) ? v : def;
    }
}
