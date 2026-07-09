package com.checkmarx.eclipse.views.findings.model;

import org.eclipse.swt.graphics.Image;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

/**
 * Represents a file node in the findings tree.
 * Contains file metadata, issue counts grouped by severity, and file type icon.
 * Icon is resolved at node creation time, following the JetBrains plugin pattern.
 */
public class FileNodeLabel {

    private final String fileName;
    private final String filePath;
    private final List<ScanIssue> issues;
    private final Map<String, Long> problemCount;
    private final Image icon;

    public FileNodeLabel(String fileName, String filePath, List<ScanIssue> issues) {
        this(fileName, filePath, issues, null, null);
    }

    public FileNodeLabel(String fileName, String filePath, List<ScanIssue> issues, Image icon) {
        this(fileName, filePath, issues, calculateProblemCount(issues), icon);
    }

    public FileNodeLabel(String fileName, String filePath, List<ScanIssue> issues, Map<String, Long> problemCount, Image icon) {
        this.fileName = fileName;
        this.filePath = filePath;
        this.issues = issues;
        this.problemCount = problemCount != null ? problemCount : calculateProblemCount(issues);
        this.icon = icon;
    }

    /**
     * Calculate problem counts grouped by severity.
     */
    private static Map<String, Long> calculateProblemCount(List<ScanIssue> issues) {
        Map<String, Long> counts = new HashMap<>();

        if (issues == null || issues.isEmpty()) {
            return counts;
        }

        for (ScanIssue issue : issues) {
            String severity = issue.getSeverity();
            if (severity != null) {
                counts.put(severity, counts.getOrDefault(severity, 0L) + 1);
            }
        }

        return counts;
    }

    public String getFileName() {
        return fileName;
    }

    public String getFilePath() {
        return filePath;
    }

    public List<ScanIssue> getIssues() {
        return issues;
    }

    public Map<String, Long> getProblemCount() {
        return problemCount;
    }

    public Image getIcon() {
        return icon;
    }

    @Override
    public String toString() {
        return fileName;
    }
}
