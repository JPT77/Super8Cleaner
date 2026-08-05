package de.jpt.super8;

import org.bytedeco.javacpp.indexer.UByteIndexer;
import org.bytedeco.opencv.opencv_core.Mat;

public class BrightnessProfiles {

	public static double[] verticalProfile(Mat img, UByteIndexer idx) {
		int rows = img.rows();
		int cols = img.cols();
		double[] profile = new double[cols];
		for (int x = 0; x < cols; x++) {
			double sum = 0.0;
			for (int y = 0; y < rows; y++) {
				int b = idx.get(y, x, 0);
				int g = idx.get(y, x, 1);
				int r = idx.get(y, x, 2);
				sum += (b + g + r) / 3.0;
			}
			profile[x] = sum / rows;
		}
		return profile;
	}

	public static double[] horizontalProfile(Mat img, UByteIndexer idx) {
		int rows = img.rows();
		int cols = img.cols();
		double[] profile = new double[rows];
		for (int y = 0; y < rows; y++) {
			double sum = 0.0;
			for (int x = 0; x < cols; x++) {
				int b = idx.get(y, x, 0);
				int g = idx.get(y, x, 1);
				int r = idx.get(y, x, 2);
				sum += (b + g + r) / 3.0;
			}
			profile[y] = sum / cols;
		}
		return profile;
	}

}