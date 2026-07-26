package de.jpt.super8;

import javax.swing.JLabel;
import javax.swing.JPanel;

import net.miginfocom.swing.MigLayout;

/** Statusleiste: Frame | FPS | Zeit | Aufloesung | Dateiname. */
public class StatusBar extends JPanel {

    private static final long serialVersionUID = 1L;

    private final JLabel lblFrame = new JLabel("Frame 0 / 0");
    private final JLabel lblFps = new JLabel("0 fps");
    private final JLabel lblTime = new JLabel("00:00.000");
    private final JLabel lblSize = new JLabel("0 x 0");
    private final JLabel lblFile = new JLabel("-");

    public StatusBar() {
        setLayout(new MigLayout(
                "insets 2 6 2 6",
                "[180!][110!][130!][130!][grow]",
                "[]"));
        add(lblFrame);
        add(lblFps);
        add(lblTime);
        add(lblSize);
        add(lblFile, "growx");
    }

    public void update(VideoInfo info) {
        lblFrame.setText("Frame " + info.currentFrame + " / " + (info.totalFrames - 1));
        lblFps.setText(String.format("%.3f fps", info.fps));
        lblTime.setText(ImageUtils.formatTime(info.getPositionMillis()));
        lblSize.setText(info.width + " x " + info.height);
        lblFile.setText(info.fileName);
    }
}
