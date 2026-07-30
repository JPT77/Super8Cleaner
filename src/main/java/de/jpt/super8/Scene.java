package de.jpt.super8;

public class Scene {

	private int start;
	private int end;
	private String name;

	public Scene(String name, int start, int end) {
		this.name = name;
		this.start = start;
		this.end = end;
	}

	public int getStart() {
		return start;
	}

	public int getEnd() {
		return end;
	}

	public void setStart(int s) {
		start = s;
	}

	public void setEnd(int e) {
		end = e;
	}

	public String getName() {
		return name;
	}

	@Override
	public String toString() {
		return name + "  [" + start + " - " + end + "]";
	}

}