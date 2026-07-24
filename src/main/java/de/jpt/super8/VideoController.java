package de.jpt.super8;

import java.awt.image.BufferedImage;
import java.io.File;

import org.opencv.core.Mat;
import org.opencv.videoio.VideoCapture;
import org.opencv.videoio.Videoio;

public class VideoController {

    private VideoCapture capture;

    private final VideoInfo info = new VideoInfo();

    public VideoInfo getInfo() {
        return info;
    }

    public boolean isOpen() {
        return capture != null && capture.isOpened();
    }

    public boolean open(File file) {

        if (capture != null)
            capture.release();

        capture = new VideoCapture(file.getAbsolutePath());

        if (!capture.isOpened())
            return false;

        info.fileName = file.getName();

        info.width =
                (int) capture.get(Videoio.CAP_PROP_FRAME_WIDTH);

        info.height =
                (int) capture.get(Videoio.CAP_PROP_FRAME_HEIGHT);

        info.totalFrames =
                (int) capture.get(Videoio.CAP_PROP_FRAME_COUNT);

        info.fps =
                capture.get(Videoio.CAP_PROP_FPS);

        info.currentFrame = 0;

        return true;

    }

    public BufferedImage readFrame(int frame) {

        if (!isOpen())
            return null;

        frame = Math.max(0, frame);
        frame = Math.min(frame, info.totalFrames - 1);

        capture.set(
                Videoio.CAP_PROP_POS_FRAMES,
                frame);

        Mat mat = new Mat();

        if (!capture.read(mat))
            return null;

        info.currentFrame = frame;

        Mat processed = EdgeFilter.process(mat);

        return ImageUtils.matToBufferedImage(processed);
    }

    public void close() {

        if (capture != null) {

            capture.release();

            capture = null;

        }

    }

}