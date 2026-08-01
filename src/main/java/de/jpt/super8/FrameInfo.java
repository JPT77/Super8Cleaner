package de.jpt.super8;

/**
 * Datenobjekt fuer ein einzelnes analysiertes Video-Frame.
 *
 * Wird von {@link FrameAnalysisWindow} pro Frame und Lauf befuellt.
 * Aktuell werden im ersten Lauf die vertikalen Minima des Helligkeits-
 * profils im oberen und unteren Drittel gesucht.
 */
public class FrameInfo {

    /** 0-basierte Framenummer im Quellvideo. */
    public final int frameNumber;

    /** Zeitposition (Millisekunden) berechnet aus Framenummer und fps. */
    public final long timestampMs;

    // ---- Lauf 1: vertikale Randminima ----
    /** Zeile des staerksten lokalen Minimums im OBEREN Drittel (-1 = nicht gefunden). */
    public int topMinRow = -1;
    /** Zeile des staerksten lokalen Minimums im UNTEREN Drittel (-1 = nicht gefunden). */
    public int bottomMinRow = -1;
    /** Abstand bottomMinRow - topMinRow in Pixel (-1 falls unbestimmt). */
    public int distance = -1;

    // ---- Lauf 1: Pilotloch (in den linken 80 px des Frames) ----
    /** Erste Zeile des detektierten Lochbereichs (>=90% der Max-Helligkeit), -1 falls nicht gefunden. */
    public int holeTop = -1;
    /** Letzte Zeile des detektierten Lochbereichs, -1 falls nicht gefunden. */
    public int holeBottom = -1;
    /** Mittelpunkt (Zeile) des Lochs, -1 falls nicht gefunden. */
    public int holeCenter = -1;
    /** Hoehe des Lochs (holeBottom - holeTop + 1), -1 falls nicht gefunden. */
    public int holeHeight = -1;
    /** Vorzeichenbehafteter Abstand holeCenter - topMinRow, Integer.MIN_VALUE falls nicht ermittelbar. */
    public int holeToTopMin = Integer.MIN_VALUE;
    /** Vorzeichenbehafteter Abstand bottomMinRow - holeCenter, Integer.MIN_VALUE falls nicht ermittelbar. */
    public int holeToBottomMin = Integer.MIN_VALUE;

    // ---- Lauf 2: horizontale Randminima (Spalten) ----
    /** Staerkstes lokales Minimum im linken Drittel des OBEREN Bandes (-1 = nicht gefunden). */
    public int leftBorderUp    = -1;
    /** Staerkstes lokales Minimum im rechten Drittel des OBEREN Bandes (-1 = nicht gefunden). */
    public int rightBorderUp   = -1;
    /** Staerkstes lokales Minimum im linken Drittel des UNTEREN Bandes (-1 = nicht gefunden). */
    public int leftBorderDown  = -1;
    /** Staerkstes lokales Minimum im rechten Drittel des UNTEREN Bandes (-1 = nicht gefunden). */
    public int rightBorderDown = -1;

    /** Frame wurde als unbrauchbar markiert (siehe {@link #badReason}). */
    public boolean bad = false;
    /** Kurze Begruendung fuer {@link #bad}. */
    public String badReason = null;

    public FrameInfo(int frameNumber, long timestampMs) {
        this.frameNumber = frameNumber;
        this.timestampMs = timestampMs;
    }

    /** Formatierter Zeitstempel mm:ss.mmm. */
    public String formattedTime() {
        long ms = timestampMs;
        long m = ms / 60_000;
        long s = (ms / 1000) % 60;
        long msRest = ms % 1000;
        return String.format("%02d:%02d.%03d", m, s, msRest);
    }

    @Override
    public String toString() {
        String hole = (holeCenter < 0)?
                        "hole=--" : String.format("hole=[%d..%d, c=%d, h=%d]", holeTop, holeBottom, holeCenter, holeHeight);
        String rel = "";
        if (holeToTopMin != Integer.MIN_VALUE || holeToBottomMin != Integer.MIN_VALUE) {
                rel = String.format("  d(hole,top)=%s  d(bot,hole)=%s",
                                holeToTopMin == Integer.MIN_VALUE ? "--" : Integer.toString(holeToTopMin),
                                                holeToBottomMin == Integer.MIN_VALUE ? "--" : Integer.toString(holeToBottomMin));
        }
        String lr = "";
        if (leftBorderUp >= 0 || rightBorderUp >= 0 || leftBorderDown >= 0 || rightBorderDown >= 0) {
                lr = String.format("  L/R up=[%s,%s]  L/R dn=[%s,%s]",
                                leftBorderUp    < 0 ? "--" : Integer.toString(leftBorderUp),
                                rightBorderUp   < 0 ? "--" : Integer.toString(rightBorderUp),
                                leftBorderDown  < 0 ? "--" : Integer.toString(leftBorderDown),
                                rightBorderDown < 0 ? "--" : Integer.toString(rightBorderDown));
        }
        if (bad) {
                return String.format("Frame %6d @ %s   BAD   top=%d bottom=%d dist=%d  %s%s%s  (%s)",
                                frameNumber, formattedTime(),
                                topMinRow, bottomMinRow, distance, hole, rel, lr,
                                badReason == null ? "" : badReason);
        }
        return String.format("Frame %6d @ %s   top=%4d  bottom=%4d  dist=%4d  %s%s%s",
                        frameNumber, formattedTime(), topMinRow, bottomMinRow, distance, hole, rel, lr);
    }

}
