package com.checkmarx.eclipse.devassist.model;

/**
 * Represents a specific location within a file where a scan issue is detected.
 * Contains line number and character range information.
 */
public class Location {

	private int line;
	private int startIndex;
	private int endIndex;

	public Location() {
	}

	public Location(int line, int startIndex, int endIndex) {
		this.line = line;
		this.startIndex = startIndex;
		this.endIndex = endIndex;
	}

	public int getLine() {
		return line;
	}

	public void setLine(int line) {
		this.line = line;
	}

	public int getStartIndex() {
		return startIndex;
	}

	public void setStartIndex(int startIndex) {
		this.startIndex = startIndex;
	}

	public int getEndIndex() {
		return endIndex;
	}

	public void setEndIndex(int endIndex) {
		this.endIndex = endIndex;
	}
}
