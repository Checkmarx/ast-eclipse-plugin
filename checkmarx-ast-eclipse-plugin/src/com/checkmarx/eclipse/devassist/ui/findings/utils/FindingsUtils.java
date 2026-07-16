package com.checkmarx.eclipse.devassist.ui.findings.utils;

import com.checkmarx.eclipse.devassist.ui.findings.model.ScanIssue;
import com.checkmarx.eclipse.devassist.ui.findings.model.ScanEngine;

import java.util.Arrays;
import java.util.List;

/**
 * Utility methods for findings view.
 */
public class FindingsUtils {

    private static final List<String> SEVERITY_ORDER = Arrays.asList(
            "malicious", "critical", "high", "medium", "low");

    /**
     * Check if a severity level represents a problem.
     *
     * @param severity Severity level
     * @return true if severity is a problem level
     */
    public static boolean isProblem(String severity) {
        if (severity == null) {
            return false;
        }
        String lower = severity.toLowerCase();
        return lower.equals("malicious") || lower.equals("critical")
            || lower.equals("high") || lower.equals("medium") || lower.equals("low");
    }

    /**
     * Get severity order priority (lower number = higher severity).
     *
     * @param severity Severity level
     * @return Priority index (0 = highest)
     */
    public static int getSeverityPriority(String severity) {
        if (severity == null) {
            return Integer.MAX_VALUE;
        }
        int index = SEVERITY_ORDER.indexOf(severity.toLowerCase());
        return index >= 0 ? index : Integer.MAX_VALUE;
    }

    /**
     * Get formatted issue text based on scan engine type.
     *
     * @param issue Scan issue
     * @return Formatted text
     */
    public static String getFormattedIssueText(ScanIssue issue) {
        if (issue == null) {
            return "";
        }

        ScanEngine engine = issue.getScanEngine();
        if (engine == null) {
            return issue.getDescription();
        }

        switch (engine) {
            case OSS:
                return issue.getSeverity() + "-risk package: " + issue.getTitle() + "@" + issue.getPackageVersion();
            case SECRETS:
                return issue.getSeverity() + "-risk secret: " + issue.getTitle();
            case CONTAINERS:
                return issue.getSeverity() + "-risk container image: " + issue.getTitle() + ":" + issue.getImageTag();
            case ASCA:
            case IAC:
                return issue.getTitle();
            default:
                return issue.getDescription();
        }
    }

    /**
     * Extract file name from full path.
     *
     * @param filePath Full file path
     * @return File name
     */
    public static String getFileName(String filePath) {
        if (filePath == null || filePath.isEmpty()) {
            return "Unknown";
        }
        int lastSeparator = Math.max(filePath.lastIndexOf('/'), filePath.lastIndexOf('\\'));
        if (lastSeparator >= 0) {
            return filePath.substring(lastSeparator + 1);
        }
        return filePath;
    }
}
