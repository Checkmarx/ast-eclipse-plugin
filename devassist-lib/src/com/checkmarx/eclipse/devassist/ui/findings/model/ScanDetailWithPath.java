package com.checkmarx.eclipse.devassist.ui.findings.model;

import com.checkmarx.eclipse.devassist.model.ScanIssue;

/**
 * Represents a scan issue with its associated file path.
 * Used as a leaf node in the findings tree.
 */
public class ScanDetailWithPath {

    private final ScanIssue detail;
    private final String filePath;
    private final FileNodeLabel parentNode;

    public ScanDetailWithPath(ScanIssue detail, String filePath, FileNodeLabel parentNode) {
        this.detail = detail;
        this.filePath = filePath;
        this.parentNode = parentNode;
    }

    public FileNodeLabel getParentNode() {
        return parentNode;
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
