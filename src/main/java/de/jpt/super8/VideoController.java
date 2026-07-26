package de.jpt.super8;

import java.awt.image.BufferedImage;
import java.io.File;

import org.opencv.core.Mat;
import org.opencv.videoio.VideoCapture;
import org.opencv.videoio.Videoio;

/**
 * Kapselt saemtlichen OpenCV-Zugriff (VideoCapture). Die GUI verwendet niemals
 * OpenCV direkt, sondern spricht ausschliesslich diesen Controller an und erhaelt
 * fertige {@link BufferedImage}s zurueck.
 */
public class VideoController {

    private VideoCapture capture;
    private final VideoInfo info = new VideoInfo();
    private Mat current;   // aktuell gelesener Frame (BGR)

    public VideoInfo getInfo() {
        return info;
    }

    public boolean isOpen() {
        return capture != null && capture.isOpened();
    }

    /** Oeffnet die Videodatei und liest die Metadaten. */
    public boolean open(File file) {
        close();
        capture = new VideoCapture(file.getAbsolutePath());
        if (!capture.isOpened()) {
            return false;
        }
        info.fileName = file.getName();
        info.width = (int) capture.get(Videoio.CAP_PROP_FRAME_WIDTH);
        info.height = (int) capture.get(Videoio.CAP_PROP_FRAME_HEIGHT);
        info.totalFrames = (int) capture.get(Videoio.CAP_PROP_FRAME_COUNT);
        info.fps = capture.get(Videoio.CAP_PROP_FPS);
        info.currentFrame = -1;
        return true;
    }

    /** Springt framegenau zu {@code frame} und liest ihn. */
    public boolean seek(int frame) {
        if (!isOpen()) {
            return false;
        }
        frame = Math.max(0, Math.min(frame, Math.max(0, info.totalFrames - 1)));
        capture.set(Videoio.CAP_PROP_POS_FRAMES, frame);
        Mat m = new Mat();
        if (!capture.read(m)) {
            return false;
        }
        if (current != null) {
            current.release();
        }
        current = m;
        info.currentFrame = frame;
        return true;
    }

    /** Originalbild des aktuellen Frames. */
    public BufferedImage getOriginalImage() {
        return current == null ? null : ImageUtils.matToBufferedImage(current);
    }

    /** Verarbeitetes Bild des aktuellen Frames (optional Canny-Kantenerkennung). */
    public BufferedImage getProcessedImage(boolean canny) {
        if (current == null) {
            return null;
        }
        if (!canny) {
            return ImageUtils.matToBufferedImage(current);
        }
        Mat edges = EdgeFilter.process(current);
        BufferedImage img = ImageUtils.matToBufferedImage(edges);
        edges.release();
        return img;
    }

    public void close() {
        if (current != null) {
            current.release();
            current = null;
        }
        if (capture != null) {
            capture.release();
            capture = null;
        }
    }
}
