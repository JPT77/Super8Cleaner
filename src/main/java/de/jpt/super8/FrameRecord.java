package de.jpt.super8;

/** Eine Zeile der analysis.csv (pro Frame). */
public class FrameRecord {
    public int frame;
    public boolean valid;
    public double contentScore;
    public int left = -1, right = -1, top = -1, bottom = -1;
    public double sceneScore = Double.NaN;
    public boolean sceneCut = false;
    public int sceneId = -1;

    public static String csvHeader() {
        return "frame,valid,content_score,left,right,top,bottom,scene_score,scene_cut,scene_id";
    }

    public String toCsvRow() {
        StringBuilder sb = new StringBuilder();
        sb.append(frame).append(',')
          .append(valid ? 1 : 0).append(',')
          .append(round(contentScore, 2)).append(',')
          .append(valid ? left : "").append(',')
          .append(valid ? right : "").append(',')
          .append(valid ? top : "").append(',')
          .append(valid ? bottom : "").append(',')
          .append(valid ? round(sceneScore, 3) : "").append(',')
          .append(sceneCut ? 1 : 0).append(',')
          .append(valid ? Integer.toString(sceneId) : "");
        return sb.toString();
    }

    private static String round(double v, int decimals) {
        double f = Math.pow(10, decimals);
        return String.valueOf(Math.round(v * f) / f);
    }
}
