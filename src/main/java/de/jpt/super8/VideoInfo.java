package de.jpt.super8;

/** Reiner Datencontainer mit den Metadaten des aktuell geoeffneten Videos. */
public class VideoInfo {

    public String fileName = "";
    public int width;
    public int height;
    public int totalFrames;
    public int currentFrame;
    public double fps;

    /** Zeitposition des aktuellen Frames in Millisekunden. */
    public long getPositionMillis() {
        return fps > 0 ? (long) (currentFrame * 1000.0 / fps) : 0;
    }
}
