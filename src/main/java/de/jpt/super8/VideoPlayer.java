package de.jpt.super8;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseWheelListener;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.File;

import javax.swing.AbstractButton;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JToolBar;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.videoio.VideoCapture;
import org.opencv.videoio.Videoio;

import net.miginfocom.swing.MigLayout;

public class VideoPlayer extends JFrame {

	private static final long serialVersionUID = 1L;

	private final VideoController controller =
			new VideoController();

	private final VideoPanel originalPanel = new VideoPanel();
	private final VideoPanel processedPanel = new VideoPanel();
	private final StatusBar statusBar = new StatusBar();
	private final JButton btnOpen = new JButton("Open");
	private final JButton btnPlay = new JButton("Play");
	private final JButton btnStop = new JButton("Stop");
	private final JButton btnPrev = new JButton("<");
	private final JButton btnNext = new JButton(">");
	private final JCheckBox chkEdges = new JCheckBox("Canny", true);
	private final JSlider slider = new JSlider();
	private Timer playTimer;

	private VideoCapture capture;

	private JOptionPane frameSlider;

	private AbstractButton lblFrame;

	public VideoPlayer() {
		super("Super8");
		setDefaultCloseOperation(EXIT_ON_CLOSE);
		buildGui();
		installEvents();
		pack();
		setLocationRelativeTo(null);

	}

	private void buildGui() {

		JPanel root =
				new JPanel(
						new MigLayout(
								"fill,insets 5",
								"[grow][grow]",
								"[][grow][]10[]"));

		//--------------------------------------------------------
		// Toolbar
		//--------------------------------------------------------

		JToolBar tb =
				new JToolBar();

		tb.setFloatable(false);
		tb.add(btnOpen);
		tb.addSeparator();
		tb.add(btnPlay);
		tb.add(btnStop);
		tb.addSeparator();
		tb.add(btnPrev);
		tb.add(btnNext);
		tb.addSeparator();
		tb.add(chkEdges);

		root.add(
				tb,
				"span 2,growx,wrap");

		//--------------------------------------------------------
		// Videos
		//--------------------------------------------------------

		JScrollPane leftScroll =
				new JScrollPane(originalPanel);

		JScrollPane rightScroll =
				new JScrollPane(processedPanel);

		root.add(
				leftScroll,
				"grow,push");

		root.add(
				rightScroll,
				"grow,push,wrap");

		//--------------------------------------------------------
		// Slider
		//--------------------------------------------------------

		slider.setMinimum(0);

		slider.setMaximum(0);

		root.add(
				slider,
				"span 2,growx,wrap");

		//--------------------------------------------------------
		// Status
		//--------------------------------------------------------

		root.add(
				statusBar,
				"span 2,growx");

		//--------------------------------------------------------

		setContentPane(root);

	}

	//------------------------------------------------------------

	private void installEvents() {

		btnOpen.addActionListener(				e -> openVideo());
//		btnPrev.addActionListener(				e -> previousFrame());
//		btnNext.addActionListener(				e -> nextFrame());
		btnPlay.addActionListener(				e -> togglePlay());
		btnStop.addActionListener(				e -> stop());

		//--------------------------------------------------------
		// Slider
		//--------------------------------------------------------

		slider.addChangeListener(e -> {

			if (slider.getValueIsAdjusting())
				return;

			showFrame(
					slider.getValue());

		});

		//--------------------------------------------------------
		// Mausrad
		//--------------------------------------------------------

		MouseWheelListener wheel = e -> {

			int step = 1;

			if (e.isControlDown())
				step = 10;

			if (e.isShiftDown())
				step = 100;

			int frame =
					slider.getValue()
					+ e.getWheelRotation() * step;

			frame =
					Math.max(
							0,
							frame);

			frame =
					Math.min(
							frame,
							slider.getMaximum());

			slider.setValue(frame);

		};

		originalPanel.addMouseWheelListener(wheel);

		processedPanel.addMouseWheelListener(wheel);

//		getRootPane().registerKeyboardAction(
//				e -> previousFrame(),
//				KeyStroke.getKeyStroke(KeyEvent.VK_LEFT,0),
//				JComponent.WHEN_IN_FOCUSED_WINDOW);
//
//		getRootPane().registerKeyboardAction(
//				e -> nextFrame(),
//				KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT,0),
//				JComponent.WHEN_IN_FOCUSED_WINDOW);


		KeyStroke.getKeyStroke(
				KeyEvent.VK_RIGHT,
				InputEvent.SHIFT_DOWN_MASK);

		KeyStroke.getKeyStroke(
				KeyEvent.VK_RIGHT,
				InputEvent.CTRL_DOWN_MASK);
	}

	private void openVideo() {
		JFileChooser chooser = new JFileChooser();
		chooser.setDialogTitle("Video auswählen");
		chooser.setFileFilter(
				new javax.swing.filechooser.FileNameExtensionFilter(
						"Videos",
						"mp4", "avi", "mov", "mkv"));

		if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION)
			return;

		File file = chooser.getSelectedFile();
		System.out.println(file.getAbsolutePath());
		loadVideo(file);
	}

	private void loadVideo(File file) {

		if (capture != null)
			capture.release();

		capture = new VideoCapture(file.getAbsolutePath());

		if (!capture.isOpened()) {
			JOptionPane.showMessageDialog(
					this,
					"Video konnte nicht geöffnet werden.");
			return;
		}

		int totalFrames =
				(int) capture.get(Videoio.CAP_PROP_FRAME_COUNT);

		slider.setMaximum(totalFrames - 1);
		showFrame(0);
	}

	private void showFrame(int frameNumber) {

		capture.set(Videoio.CAP_PROP_POS_FRAMES, frameNumber);

		Mat mat = new Mat();

		if (!capture.read(mat))
			return;

		BufferedImage img = matToBufferedImage(mat);

		originalPanel.setImage(img);

		frameSlider.setValue(frameNumber);

		lblFrame.setText(
				"Frame: " + frameNumber + " / " +
						slider.getMaximum());
	}

	public static BufferedImage matToBufferedImage(Mat mat) {
		if (mat == null || mat.empty()) {
			return null;
		}

		int type;
		switch (mat.channels()) {
		case 1:
			type = BufferedImage.TYPE_BYTE_GRAY;
			break;
		case 3:
			type = BufferedImage.TYPE_3BYTE_BGR;
			break;
		default:
			throw new IllegalArgumentException(
					"Unsupported number of channels: " + mat.channels());
		}

		BufferedImage image = new BufferedImage(
				mat.cols(),
				mat.rows(),
				type);

		byte[] source = new byte[(int) (mat.total() * mat.channels())];
		mat.get(0, 0, source);

		byte[] target =
				((DataBufferByte) image.getRaster().getDataBuffer()).getData();

		System.arraycopy(source, 0, target, 0, source.length);

		return image;
	}

	public static void main(String[] args) {
		System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
		SwingUtilities.invokeLater(() -> {
			new VideoPlayer2().setVisible(true);
		});
	}

}