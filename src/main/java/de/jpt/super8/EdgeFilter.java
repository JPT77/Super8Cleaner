package de.jpt.super8;

import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

public class EdgeFilter {

    public static Mat process(Mat src) {

        Mat gray = new Mat();

        Mat edges = new Mat();

        Imgproc.cvtColor(
                src,
                gray,
                Imgproc.COLOR_BGR2GRAY);

        Imgproc.GaussianBlur(
                gray,
                gray,
                new Size(5,5),
                1.4);

        Imgproc.Canny(
                gray,
                edges,
                60,
                120);

        return edges;

    }

}