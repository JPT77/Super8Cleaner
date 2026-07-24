package de.jpt.super8;

/**
 * Eine Zeile der analysis.csv (pro Frame des Quellvideos).
 * Enthaelt saemtliche im Verlauf der Pipeline ermittelten Daten (Schritt 10).
 */
public class FrameRecord {
    public int frame;                 // Index im Quellvideo
    public boolean kept;              // true = nicht leer (Schritt 1)
    public double contentScore;       // Inhalts-Score

    // Roh-Crop (Schritt 2)
    public int rawLeft = -1, rawRight = -1, rawTop = -1, rawBottom = -1;
    // Korrigierter, einheitlicher Crop (Schritt 3/4)
    public int cropX = -1, cropY = -1, cropW = -1, cropH = -1;

    public double brightness = Double.NaN; // mittlere Helligkeit (0..255)

    // Diff-Klassifikation (Schritt 7); nur fuer behaltene Frames
    public double diffPrev = Double.NaN;
    public String classification = "";    // EMPTY | SCENE | NORMAL | DUP
    public int sceneId = -1;

    // Farbkorrektur-Gains der Szene (Schritt 9)
    public double gainB = Double.NaN, gainG = Double.NaN, gainR = Double.NaN;

    public static String csvHeader() {
        return "frame,kept,content_score,raw_left,raw_right,raw_top,raw_bottom,"
             + "crop_x,crop_y,crop_w,crop_h,brightness,diff_prev,classification,"
             + "scene_id,gain_b,gain_g,gain_r";
    }

    public String toCsvRow() {
        StringBuilder sb = new StringBuilder();
        sb.append(frame).append(',')
          .append(kept ? 1 : 0).append(',')
          .append(r(contentScore, 2)).append(',')
          .append(i(rawLeft)).append(',').append(i(rawRight)).append(',')
          .append(i(rawTop)).append(',').append(i(rawBottom)).append(',')
          .append(i(cropX)).append(',').append(i(cropY)).append(',')
          .append(i(cropW)).append(',').append(i(cropH)).append(',')
          .append(r(brightness, 1)).append(',')
          .append(r(diffPrev, 4)).append(',')
          .append(classification).append(',')
          .append(sceneId < 0 ? "" : Integer.toString(sceneId)).append(',')
          .append(r(gainB, 3)).append(',').append(r(gainG, 3)).append(',').append(r(gainR, 3));
        return sb.toString();
    }

    private static String i(int v) { return v < 0 ? "" : Integer.toString(v); }

    private static String r(double v, int decimals) {
        if (Double.isNaN(v)) return "";
        double f = Math.pow(10, decimals);
        return String.valueOf(Math.round(v * f) / f);
    }
}
