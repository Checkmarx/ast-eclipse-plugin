package com.checkmarx.eclipse.views.findings.model;

/**
 * Represents a scan issue with its associated file path.
 * Used as a leaf node in the findings tree.
 */
public class ScanDetailWithPath {

    private final ScanIssue detail;
    private final String filePath;

    public ScanDetailWithPath(ScanIssue detail, String filePath) {
        this.detail = detail;
        this.filePath = filePath;
    }

    public ScanIssue getDetail() {
        return detail;
    }

    public String getFilePath() {
        return filePath;
    }

    @Override
    public String toString() {
        return detail != null ? detail.getTitle() : "Unknown";
    }
}
