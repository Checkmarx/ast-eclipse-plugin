package com.checkmarx.eclipse.views.findings.marker;

import org.eclipse.core.resources.IMarker;

import com.checkmarx.eclipse.enums.Severity;
import com.checkmarx.eclipse.views.findings.model.Location;
import com.checkmarx.eclipse.views.findings.model.ScanEngine;
import com.checkmarx.eclipse.views.findings.model.ScanIssue;
import com.checkmarx.eclipse.views.problems.marker.ProblemMarkerConstants;
import com.checkmarx.eclipse.views.problems.model.ScanProblem;

/**
 * Maps between ScanIssue objects and IMarker attributes.
 * This is the single source of truth for marker attribute serialization.
 * Allows marker resolution to reconstruct finding details without searching.
 */
public class MarkerIssueMapper {

    // Marker attribute names (prefixed with cx. to avoid collision)
    private static final String ATTR_ISSUE_ID = "cx.issueId";
    private static final String ATTR_SEVERITY = "cx.severity";
    private static final String ATTR_TITLE = "cx.title";
    private static final String ATTR_DESCRIPTION = "cx.description";
    private static final String ATTR_REMEDIATION = "cx.remediation";
    private static final String ATTR_RULE_ID = "cx.ruleId";
    private static final String ATTR_FILE_PATH = "cx.filePath";
    private static final String ATTR_SCAN_ENGINE = "cx.scanEngine";

    /**
     * Reconstruct a ScanIssue from marker attributes.
     * Called by marker resolution to populate the details dialog.
     *
     * @param marker the IMarker containing serialized issue data
     * @return reconstructed ScanIssue, or null if reconstruction fails
     */
    public static ScanIssue fromMarker(IMarker marker) {
        try {
            String issueId = marker.getAttribute(ATTR_ISSUE_ID, "");
            String severity = marker.getAttribute(ATTR_SEVERITY, "MEDIUM");
            String title = marker.getAttribute(ATTR_TITLE, marker.getAttribute(IMarker.MESSAGE, ""));
            String description = marker.getAttribute(ATTR_DESCRIPTION, "");
            String remediation = marker.getAttribute(ATTR_REMEDIATION, null);
            Integer ruleId = null;
            try {
                Object ruleIdObj = marker.getAttribute(ATTR_RULE_ID);
                if (ruleIdObj instanceof Integer) {
                    ruleId = (Integer) ruleIdObj;
                } else if (ruleIdObj instanceof String && !ruleIdObj.toString().isEmpty()) {
                    ruleId = Integer.parseInt(ruleIdObj.toString());
                }
            } catch (Exception e) {
                // Keep ruleId as null
            }
            String filePath = marker.getAttribute(ATTR_FILE_PATH, "");
            String scanEngineStr = marker.getAttribute(ATTR_SCAN_ENGINE, "ASCA");
            int lineNumber = marker.getAttribute(IMarker.LINE_NUMBER, 1);
            int charStart = marker.getAttribute(IMarker.CHAR_START, 0);
            int charEnd = marker.getAttribute(IMarker.CHAR_END, 0);

            // Reconstruct ScanIssue
            ScanIssue issue = new ScanIssue();
            issue.setScanIssueId(issueId);
            issue.setSeverity(severity);
            issue.setTitle(title);
            issue.setDescription(description);
            issue.setRemediationAdvise(remediation);
            issue.setRuleId(ruleId);
            issue.setFilePath(filePath);

            // Parse scan engine
            try {
                issue.setScanEngine(ScanEngine.valueOf(scanEngineStr));
            } catch (IllegalArgumentException e) {
                issue.setScanEngine(ScanEngine.ASCA);
            }

            // Reconstruct location
            Location location = new Location();
            location.setLine(lineNumber);
            location.setStartIndex(charStart);
            location.setEndIndex(charEnd);
            issue.setLocations(java.util.Collections.singletonList(location));

            return issue;
        } catch (Exception e) {
            System.out.println("[MARKER-MAPPER] Error reconstructing ScanIssue from marker: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Populate marker attributes from a ScanIssue.
     * Called when creating markers from findings.
     *
     * @param marker the IMarker to populate
     * @param issue the ScanIssue containing data to serialize
     */
    public static void populateMarker(IMarker marker, ScanIssue issue) {
        try {
            if (issue.getScanIssueId() != null && !issue.getScanIssueId().isEmpty()) {
                marker.setAttribute(ATTR_ISSUE_ID, issue.getScanIssueId());
            }

            if (issue.getSeverity() != null && !issue.getSeverity().isEmpty()) {
                marker.setAttribute(ATTR_SEVERITY, issue.getSeverity());
            }

            if (issue.getTitle() != null && !issue.getTitle().isEmpty()) {
                marker.setAttribute(ATTR_TITLE, issue.getTitle());
                // Also set MESSAGE for default marker hover display
                marker.setAttribute(IMarker.MESSAGE, issue.getTitle());
            }

            if (issue.getDescription() != null && !issue.getDescription().isEmpty()) {
                marker.setAttribute(ATTR_DESCRIPTION, issue.getDescription());
            }

            if (issue.getRemediationAdvise() != null && !issue.getRemediationAdvise().isEmpty()) {
                marker.setAttribute(ATTR_REMEDIATION, issue.getRemediationAdvise());
            }

            if (issue.getRuleId() != null) {
                marker.setAttribute(ATTR_RULE_ID, issue.getRuleId());
            }

            if (issue.getFilePath() != null && !issue.getFilePath().isEmpty()) {
                marker.setAttribute(ATTR_FILE_PATH, issue.getFilePath());
            }

            if (issue.getScanEngine() != null) {
                marker.setAttribute(ATTR_SCAN_ENGINE, issue.getScanEngine().toString());
            }

            // Set standard marker attributes from location
            if (issue.getLocations() != null && !issue.getLocations().isEmpty()) {
                Location location = issue.getLocations().get(0);
                marker.setAttribute(IMarker.LINE_NUMBER, location.getLine());
                marker.setAttribute(IMarker.CHAR_START, location.getStartIndex());
                marker.setAttribute(IMarker.CHAR_END, location.getEndIndex());

                // Calculate severity for Eclipse marker system (0=info, 1=warning, 2=error)
                int severity = calculateMarkerSeverity(issue.getSeverity());
                marker.setAttribute(IMarker.SEVERITY, severity);
            }

            System.out.println("[MARKER-MAPPER] Populated marker for issue: " + issue.getTitle());

        } catch (Exception e) {
            System.out.println("[MARKER-MAPPER] Error populating marker: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Populate marker attributes from a ScanProblem (used by Problems View).
     * Called when creating markers from problems.
     *
     * @param marker the IMarker to populate
     * @param problem the ScanProblem containing data to serialize
     */
    public static void populateMarkerFromProblem(IMarker marker, ScanProblem problem) {
        try {
            if (problem.getId() != null && !problem.getId().isEmpty()) {
                marker.setAttribute(ProblemMarkerConstants.ATTR_FINDING_ID, problem.getId());
            }

            if (problem.getMessage() != null && !problem.getMessage().isEmpty()) {
                marker.setAttribute(IMarker.MESSAGE, problem.getMessage());
            }

            if (problem.getRuleId() != null && !problem.getRuleId().isEmpty()) {
                marker.setAttribute(ProblemMarkerConstants.ATTR_RULE_ID, problem.getRuleId());
            }

            if (problem.getSeverity() != null) {
                marker.setAttribute(ProblemMarkerConstants.ATTR_SEVERITY, problem.getSeverity().name());
                marker.setAttribute(IMarker.SEVERITY, toEclipseSeverity(problem.getSeverity()));
            }

            if (problem.getStatus() != null && !problem.getStatus().isEmpty()) {
                marker.setAttribute(ProblemMarkerConstants.ATTR_STATUS, problem.getStatus());
            }

            System.out.println("[MARKER-MAPPER] Populated marker from problem: " + problem.getMessage());

        } catch (Exception e) {
            System.out.println("[MARKER-MAPPER] Error populating marker from problem: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Convert Checkmarx severity to Eclipse marker severity level.
     */
    private static int calculateMarkerSeverity(String severity) {
        if (severity == null) {
            return IMarker.SEVERITY_WARNING;
        }

        switch (severity.toLowerCase()) {
            case "critical":
            case "high":
                return IMarker.SEVERITY_ERROR;
            case "medium":
                return IMarker.SEVERITY_WARNING;
            case "low":
            case "info":
                return IMarker.SEVERITY_INFO;
            default:
                return IMarker.SEVERITY_WARNING;
        }
    }

    /**
     * Convert Checkmarx Severity enum to Eclipse marker severity level.
     */
    private static int toEclipseSeverity(Severity severity) {
        if (severity == null) {
            return IMarker.SEVERITY_WARNING;
        }

        switch (severity) {
            case CRITICAL:
            case HIGH:
                return IMarker.SEVERITY_ERROR;
            case MEDIUM:
                return IMarker.SEVERITY_WARNING;
            case LOW:
            case INFO:
            default:
                return IMarker.SEVERITY_INFO;
        }
    }
}
