package de.jpt.super8;
import org.opencv.core.Mat;

public class BrightnessProfiles {

    public static double[] verticalProfile(Mat img) {

        int rows = img.rows();
        int cols = img.cols();

        double[] profile = new double[cols];

        for(int x=0;x<cols;x++) {

            double sum = 0;

            for(int y=0;y<rows;y++) {

                double[] p = img.get(y,x);

                double gray = (p[0]+p[1]+p[2])/3.0;

                sum += gray;
            }

            profile[x]=sum/rows;
        }

        return profile;
    }

    public static double[] horizontalProfile(Mat img) {

        int rows = img.rows();
        int cols = img.cols();

        double[] profile = new double[rows];

        for(int y=0;y<rows;y++) {

            double sum = 0;

            for(int x=0;x<cols;x++) {

                double[] p = img.get(y,x);

                double gray = (p[0]+p[1]+p[2])/3.0;

                sum += gray;
            }

            profile[y]=sum/cols;
        }

        return profile;
    }

}