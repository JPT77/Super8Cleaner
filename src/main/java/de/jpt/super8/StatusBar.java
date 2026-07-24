package de.jpt.super8;

import java.awt.Dimension;

import javax.swing.JLabel;
import javax.swing.JPanel;

import net.miginfocom.swing.MigLayout;

public class StatusBar extends JPanel {

    private final JLabel lblFrame =
            new JLabel("Frame");

    private final JLabel lblSize =
            new JLabel("Size");

    private final JLabel lblFPS =
            new JLabel("FPS");

    private final JLabel lblTime =
            new JLabel("00:00");

    private final JLabel lblFile =
            new JLabel("");

    public StatusBar() {

        setLayout(new MigLayout(
                "fill,insets 2",
                "[][grow][][][]",
                "[]"));

        lblFrame.setPreferredSize(
                new Dimension(170,20));

        lblSize.setPreferredSize(
                new Dimension(110,20));

        lblFPS.setPreferredSize(
                new Dimension(90,20));

        lblTime.setPreferredSize(
                new Dimension(100,20));

        add(lblFrame);

        add(lblFile,"growx");

        add(lblSize);

        add(lblFPS);

        add(lblTime);

    }

    public void update(VideoInfo info) {

        lblFrame.setText(
                "Frame "
                        + info.currentFrame
                        + " / "
                        + info.totalFrames);

        lblSize.setText(
                info.width
                        + " x "
                        + info.height);

        lblFPS.setText(
                String.format(
                        "%.3f fps",
                        info.fps));

        lblTime.setText(
                ImageUtils.formatTime(
                        info.getPositionMillis()));

        lblFile.setText(
                info.fileName);

    }

}