package de.jpt.super8;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;

import org.opencv.core.CvType;
import org.opencv.core.Mat;

/** Hilfsfunktionen: Mat &harr; BufferedImage sowie Zeitformatierung. */
public final class ImageUtils {

    private ImageUtils() {
    }

    /** OpenCV-Mat (1- oder 3-kanalig, BGR) -> BufferedImage. */
    public static BufferedImage matToBufferedImage(Mat mat) {
        if (mat == null || mat.empty()) {
            return null;
        }
        int type = mat.channels() == 1
                ? BufferedImage.TYPE_BYTE_GRAY
                : BufferedImage.TYPE_3BYTE_BGR;
        BufferedImage img = new BufferedImage(mat.cols(), mat.rows(), type);
        byte[] src = new byte[(int) (mat.total() * mat.channels())];
        mat.get(0, 0, src);
        byte[] dst = ((DataBufferByte) img.getRaster().getDataBuffer()).getData();
        System.arraycopy(src, 0, dst, 0, src.length);
        return img;
    }

    /** BufferedImage -> OpenCV-Mat (3-kanalig, BGR). */
    public static Mat bufferedImageToMat(BufferedImage bi) {
        BufferedImage img = bi;
        if (bi.getType() != BufferedImage.TYPE_3BYTE_BGR) {
            img = new BufferedImage(bi.getWidth(), bi.getHeight(), BufferedImage.TYPE_3BYTE_BGR);
            img.getGraphics().drawImage(bi, 0, 0, null);
        }
        byte[] data = ((DataBufferByte) img.getRaster().getDataBuffer()).getData();
        Mat mat = new Mat(img.getHeight(), img.getWidth(), CvType.CV_8UC3);
        mat.put(0, 0, data);
        return mat;
    }

    /** Millisekunden -> "HH:MM:SS.mmm" (bzw. "MM:SS.mmm"). */
    public static String formatTime(long ms) {
        long h = ms / 3_600_000; ms %= 3_600_000;
        long m = ms / 60_000;    ms %= 60_000;
        long s = ms / 1_000;     ms %= 1_000;
        return h > 0
                ? String.format("%02d:%02d:%02d.%03d", h, m, s, ms)
                : String.format("%02d:%02d.%03d", m, s, ms);
    }
}
