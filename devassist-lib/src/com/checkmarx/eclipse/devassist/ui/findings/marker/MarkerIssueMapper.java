package com.checkmarx.eclipse.devassist.ui.findings.marker;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IMarker;

import com.checkmarx.eclipse.common.enums.Severity;
import com.checkmarx.eclipse.devassist.model.Location;
import com.checkmarx.eclipse.devassist.model.ScanEngine;
import com.checkmarx.eclipse.devassist.model.ScanIssue;
import com.checkmarx.eclipse.devassist.model.Vulnerability;

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
    public static final String ATTR_SCAN_ENGINE = "cx.scanEngine";
    private static final String ATTR_VULNERABILITIES = "cx.vulnerabilities";

    // Delimiters for the flat vulnerabilities encoding. These control characters
    // (unit separator / record separator) can't legally appear in marker text
    // (title/description), unlike printable characters such as commas or pipes.
    private static final String VULN_FIELD_SEP = "";
    private static final String VULN_RECORD_SEP = "";

    /**
     * Reads the Checkmarx issue id off a marker without needing a full
     * fromMarker() reconstruction - used by the hover to cross-reference a
     * MarkerAnnotation against an already-rendered FindingsAnnotation for the
     * same underlying finding.
     */
    public static String getIssueId(IMarker marker) {
        return marker.getAttribute(ATTR_ISSUE_ID, "");
    }

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
            String vulnerabilitiesRaw = marker.getAttribute(ATTR_VULNERABILITIES, "");

            // Reconstruct ScanIssue
            ScanIssue issue = new ScanIssue();
            issue.setScanIssueId(issueId);
            issue.setSeverity(severity);
            issue.setTitle(title);
            issue.setDescription(description);
            issue.setRemediationAdvise(remediation);
            issue.setRuleId(ruleId);
            issue.setFilePath(filePath);
            if (!vulnerabilitiesRaw.isEmpty()) {
                issue.setVulnerabilities(decodeVulnerabilities(vulnerabilitiesRaw));
            }

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

            // Carry the full vulnerabilities list (ASCA/IAC can group several
            // vulnerabilities under one issue) so marker-based hover/details
            // reconstruction doesn't collapse back down to a single entry.
            if (issue.getVulnerabilities() != null && !issue.getVulnerabilities().isEmpty()) {
                marker.setAttribute(ATTR_VULNERABILITIES, encodeVulnerabilities(issue.getVulnerabilities()));
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



        } catch (Exception e) {

            e.printStackTrace();
        }
    }

    /**
     * Flattens title/description pairs into one marker-attribute-safe string.
     */
    private static String encodeVulnerabilities(List<Vulnerability> vulnerabilities) {
        StringBuilder sb = new StringBuilder();
        for (Vulnerability vuln : vulnerabilities) {
            if (sb.length() > 0) {
                sb.append(VULN_RECORD_SEP);
            }
            sb.append(sanitize(vuln.getTitle())).append(VULN_FIELD_SEP).append(sanitize(vuln.getDescription()));
        }
        return sb.toString();
    }

    private static List<Vulnerability> decodeVulnerabilities(String raw) {
        List<Vulnerability> result = new ArrayList<>();
        for (String record : raw.split(VULN_RECORD_SEP, -1)) {
            if (record.isEmpty()) {
                continue;
            }
            String[] fields = record.split(VULN_FIELD_SEP, -1);
            Vulnerability vuln = new Vulnerability();
            vuln.setTitle(fields.length > 0 ? fields[0] : "");
            vuln.setDescription(fields.length > 1 ? fields[1] : "");
            result.add(vuln);
        }
        return result;
    }

    private static String sanitize(String value) {
        if (value == null) {
            return "";
        }
        return value.replace(VULN_FIELD_SEP, " ").replace(VULN_RECORD_SEP, " ");
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
