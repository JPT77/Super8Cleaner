package de.jpt.super8;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public class GaussianPanel extends JPanel {

    private static final int LEFT = 50;
    private static final int RIGHT = 20;
    private static final int TOP = 25;
    private static final int BOTTOM = 35;

    private final List<Double> history;


    public GaussianPanel(List<Double> history) {

        this.history = history;

        setPreferredSize(
                new Dimension(900, 250));

        setBackground(Color.WHITE);
    }


    @Override
    protected void paintComponent(Graphics g) {

        super.paintComponent(g);

        Graphics2D g2 =
                (Graphics2D) g.create();


        g2.setRenderingHint(
                RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);


        int width = getWidth();
        int height = getHeight();


        int graphWidth =
                width - LEFT - RIGHT;

        int graphHeight =
                height - TOP - BOTTOM;



        // Achsen

        g2.setColor(Color.BLACK);

        g2.drawLine(
                LEFT,
                TOP + graphHeight,
                LEFT + graphWidth,
                TOP + graphHeight);

        g2.drawLine(
                LEFT,
                TOP,
                LEFT,
                TOP + graphHeight);



        if (history.size() < 2) {

            g2.dispose();
            return;
        }



        // -------------------------------
        // Statistik
        // -------------------------------

        double mean = mean(history);
        double sigma = stdDev(history, mean);


        if (sigma < 0.0001) {

            sigma = 1;
        }



        double min =
                mean - 4 * sigma;

        double max =
                mean + 4 * sigma;



        double maxY =
                gaussian(mean, mean, sigma);



        // -------------------------------
        // Gitternetz
        // -------------------------------

        g2.setColor(
                new Color(230,230,230));


        for(int i=0;i<=4;i++) {

            int y =
                    TOP +
                    graphHeight -
                    i * graphHeight / 4;


            g2.drawLine(
                    LEFT,
                    y,
                    LEFT+graphWidth,
                    y);
        }



        // -------------------------------
        // Glockenkurve
        // -------------------------------

        g2.setColor(
                new Color(40,90,220));


        int oldX = -1;
        int oldY = -1;


        for(int x=0;x<=graphWidth;x++) {


            double value =
                    min +
                    (max-min)
                    * x
                    / graphWidth;



            double yValue =
                    gaussian(
                            value,
                            mean,
                            sigma);



            int y =
                    TOP +
                    graphHeight -
                    (int)(
                    (yValue/maxY)
                    * graphHeight);



            if(oldX >= 0) {

                g2.drawLine(
                        oldX,
                        oldY,
                        LEFT+x,
                        y);
            }


            oldX =
                    LEFT+x;

            oldY =
                    y;
        }



        // -------------------------------
        // Mittelwert μ
        // -------------------------------

        int meanX =
                LEFT +
                (int)(
                (mean-min)
                /(max-min)
                * graphWidth);


        g2.setColor(
                new Color(0,150,0));


        g2.drawLine(
                meanX,
                TOP,
                meanX,
                TOP+graphHeight);



        g2.drawString(
                String.format(
                        "μ %.2f",
                        mean),
                meanX+5,
                TOP+15);



        // -------------------------------
        // Aktueller Wert
        // -------------------------------

        double current =
                history.get(
                        history.size()-1);


        int currentX =
                LEFT +
                (int)(
                (current-min)
                /(max-min)
                * graphWidth);



        g2.setColor(Color.RED);


        g2.drawLine(
                currentX,
                TOP,
                currentX,
                TOP+graphHeight);


        g2.drawString(
                String.format(
                        "aktuell %.2f",
                        current),
                currentX+5,
                TOP+35);



        // -------------------------------
        // Beschriftung
        // -------------------------------

        g2.setColor(Color.BLACK);


        g2.drawString(
                "Verteilung der Frame-Helligkeiten (Normalverteilung)",
                LEFT,
                15);


        g2.drawString(
                String.format(
                        "σ = %.2f",
                        sigma),
                LEFT,
                height-10);



        g2.dispose();
    }




    private static double gaussian(
            double x,
            double mean,
            double sigma) {


        double a =
                1.0 /
                (sigma *
                Math.sqrt(
                2*Math.PI));


        double b =
                Math.exp(
                -0.5 *
                Math.pow(
                (x-mean)/sigma,
                2));


        return a*b;
    }




    private static double mean(
            List<Double> values) {


        double sum=0;


        for(double v:values)
            sum+=v;


        return sum/values.size();
    }




    private static double stdDev(
            List<Double> values,
            double mean) {


        double sum=0;


        for(double v:values) {

            double d =
                    v-mean;

            sum += d*d;
        }


        return Math.sqrt(
                sum/values.size());
    }
}