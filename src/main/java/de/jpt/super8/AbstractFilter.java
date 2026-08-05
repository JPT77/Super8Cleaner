package de.jpt.super8;

import org.bytedeco.opencv.opencv_core.Mat;

public abstract class AbstractFilter {

    private final String name;

    public AbstractFilter(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return name;
    }

	protected abstract Mat process(Mat result);

}