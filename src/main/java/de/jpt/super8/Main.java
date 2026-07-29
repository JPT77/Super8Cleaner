package de.jpt.super8;
import javax.swing.SwingUtilities;

import org.bytedeco.javacpp.Loader;
import org.bytedeco.opencv.global.opencv_core;

public class Main{

	public static void main(String[] args){
//		System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
		Loader.load(opencv_core.class);
		SwingUtilities.invokeLater(() -> new VideoPlayer().setVisible(true));
	}

}
