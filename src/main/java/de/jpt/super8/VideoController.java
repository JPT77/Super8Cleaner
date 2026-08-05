package de.jpt.super8;

import java.awt.image.BufferedImage;
import java.io.File;

import javax.swing.JList;

import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.FFmpegFrameGrabber.Exception;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.OpenCVFrameConverter;
import org.bytedeco.opencv.opencv_core.Mat;

/**
 * Kapselt saemtlichen OpenCV-Zugriff (VideoCapture). Die GUI verwendet niemals
 * OpenCV direkt, sondern spricht ausschliesslich diesen Controller an und erhaelt
 * fertige {@link BufferedImage}s zurueck.
 */
public class VideoController {

	private FFmpegFrameGrabber grabber;
	private final VideoInfo info = new VideoInfo();
	private final OpenCVFrameConverter.ToMat converter = new OpenCVFrameConverter.ToMat();
	private Mat current;

	public VideoInfo getInfo() {
		return info;
	}

	public boolean isOpen() {
		return grabber != null;
	}

	/** Oeffnet die Videodatei und liest die Metadaten. */
	public boolean open(File file) {

		close();

		try {
			System.out.println("Öffne: " + file.getAbsolutePath());
			grabber = new FFmpegFrameGrabber(file);
			grabber.start();

			info.fileName = file.getName();
			info.width = grabber.getImageWidth();
			info.height = grabber.getImageHeight();
			info.totalFrames = grabber.getLengthInFrames();
			info.fps = grabber.getFrameRate();
			info.currentFrame = 0;

			return nextFrame();

		} catch (Exception e) {
			e.printStackTrace();
			close();
			return false;
		}
	}

	/**
	 * Liest den nächsten Frame (schnell).
	 */
	public boolean nextFrame() {

		if (!isOpen())
			return false;

		try {

			Frame frame = grabber.grabImage();

			if (frame == null)
				return false;

			if (current != null)
				current.release();

			current = converter.convert(frame).clone();

			info.currentFrame++;

			return true;

		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
	}

	/**
	 * Springt zu einem Frame.
	 */
	public boolean seek(int frameNumber) {

		if (!isOpen())
			return false;

		frameNumber = Math.max(0,
				Math.min(frameNumber, info.totalFrames - 1));

		try {

			grabber.setVideoFrameNumber(frameNumber);

			Frame frame = grabber.grabImage();

			if (frame == null)
				return false;

			if (current != null)
				current.release();

			current = converter.convert(frame).clone();

			info.currentFrame = frameNumber;

			return true;

		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
	}
	/** Originalbild des aktuellen Frames. */
	public BufferedImage getOriginalImage() {
		return current == null ? null : ImageUtils.matToBufferedImage(current);
	}

	public BufferedImage getProcessedImage(JList<AbstractFilter> filterList) {
		if (current == null) {
			return null;
		}
		Mat result = current.clone();
		for (AbstractFilter filter : filterList.getSelectedValuesList()) {
			Mat next = filter.process(result);
			if (result != current) {
				result.release();
			}
			result = next;
		}
		BufferedImage image = ImageUtils.matToBufferedImage(result);
		if (result != current) {
			result.release();
		}
		return image;
	}

	public void close() {

		if (current != null) {
			current.release();
			current = null;
		}

		if (grabber != null) {
			try {
				grabber.stop();
				grabber.close();
			} catch (org.bytedeco.javacv.FrameGrabber.Exception e) {
				e.printStackTrace();
			}
			grabber = null;
		}
	}

}
