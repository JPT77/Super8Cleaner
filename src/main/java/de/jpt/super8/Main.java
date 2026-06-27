package de.jpt.super8;

import org.bytedeco.ffmpeg.global.avutil;

/** Einstiegspunkt: gradle run --args="input.mp4 output" */
public class Main {

	public static void main(String[] args) throws Exception {
		avutil.av_log_set_level(avutil.AV_LOG_ERROR); // FFmpeg-Logging reduzieren
		Config cfg = Config.fromArgs(args);
		System.out.println("Input : " + cfg.videoPath);
		System.out.println("Output: " + cfg.outputDir);
		new Super8Pipeline(cfg).run();
	}

}
