package com.checkmarx.eclipse.devassist.ui.findings.marker;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IEditorReference;
import org.eclipse.ui.IFileEditorInput;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.texteditor.ITextEditor;

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

    private static final String MARKER_TYPE = "com.checkmarx.eclipse.plugin.checkmarxProblemMarker";

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
                applyLocationAttributes(marker, issue.getLocations().get(0));

                // Calculate severity for Eclipse marker system (0=info, 1=warning, 2=error)
                int severity = calculateMarkerSeverity(issue.getSeverity());
                marker.setAttribute(IMarker.SEVERITY, severity);
            }
        } catch (Exception e) {

            e.printStackTrace();
        }
    }

    /**
     * Ensures a {@value #MARKER_TYPE} marker exists for this finding, creating and populating
     * one if none does yet. This is what CheckmarxMarkerResolutionGenerator's Ctrl+1/quick-fix-
     * in-hover actions anchor to; ProblemDecorator calls this for every issue it decorates so the
     * marker (and therefore the quick-fix actions) exists as soon as the squiggly does, instead of
     * only after the user navigates to that specific finding from the Findings view.
     *
     * @param file the file the issue was found in
     * @param issue the finding to ensure a marker for
     */
    public static void ensureMarker(IFile file, ScanIssue issue) {
        if (file == null || issue == null || issue.getLocations() == null || issue.getLocations().isEmpty()) {
            return;
        }

        try {
            if (findMarker(file, issue) != null) {
                return;
            }
            IMarker marker = file.createMarker(MARKER_TYPE);
            int lineNumber = issue.getLocations().get(0).getLine();
            marker.setAttribute(IMarker.LINE_NUMBER, lineNumber > 0 ? lineNumber : 1);
            marker.setAttribute(IMarker.MESSAGE, issue.getTitle());
            marker.setAttribute(IMarker.SEVERITY, IMarker.SEVERITY_WARNING);
            marker.setAttribute(IMarker.USER_EDITABLE, false);

            populateMarker(marker, issue);
        } catch (Exception e) {
            // Marker creation is best-effort: the squiggly annotation and CheckmarxAnnotationHover
            // (both driven by the live ScanIssue/FindingsAnnotation, not this marker) still work
            // even if this fails.
        }
    }

    /**
     * Finds the existing {@value #MARKER_TYPE} marker for a ScanIssue, matching by the stable
     * scanIssueId when available and falling back to line+title for findings without one.
     *
     * @param file the file to search
     * @param issue the finding to find a marker for
     * @return the matching marker, or null if none exists
     */
    public static IMarker findMarker(IFile file, ScanIssue issue) {
        if (file == null || issue == null || issue.getLocations() == null || issue.getLocations().isEmpty()) {
            return null;
        }

        String issueId = issue.getScanIssueId();
        int issueLine = issue.getLocations().get(0).getLine();
        String issueTitle = issue.getTitle();

        try {
            IMarker[] markers = file.findMarkers(MARKER_TYPE, true, IResource.DEPTH_ZERO);
            for (IMarker marker : markers) {
                if (issueId != null && !issueId.isEmpty()) {
                    if (issueId.equals(marker.getAttribute(ATTR_ISSUE_ID, ""))) {
                        return marker;
                    }
                    continue;
                }
                // Fallback for findings without a scanIssueId: line+title heuristic.
                int markerLine = marker.getAttribute(IMarker.LINE_NUMBER, -1);
                if (markerLine == issueLine) {
                    String markerMsg = marker.getAttribute(IMarker.MESSAGE, "");
                    if (issueTitle == null || issueTitle.isEmpty() || markerMsg.contains(issueTitle)) {
                        return marker;
                    }
                }
            }
        } catch (Exception e) {
            // fall through
        }

        return null;
    }

    /**
     * Sets IMarker.LINE_NUMBER and, when possible, IMarker.CHAR_START/CHAR_END from a
     * Location. Most scan engines (OSS, IaC, Secrets, Containers) report startIndex/endIndex
     * as offsets relative to the start of the line, not the file - writing them straight into
     * CHAR_START/CHAR_END as absolute file offsets collapses every marker onto whichever line
     * happens to contain that many characters (almost always line 1), independent of which
     * line the finding is actually on. This resolves the line's real offset in the document and
     * adds it in, mirroring the conversion ProblemDecorator already applies when positioning the
     * squiggly annotation - so the IMarker (which is what Eclipse's built-in quick-fix-in-hover
     * and Ctrl+1 machinery anchors to) lands on the same line as the squiggly instead of drifting
     * to a different one.
     *
     * @param marker the IMarker being populated
     * @param location the finding's location (line, and possibly line-relative or absolute start/end)
     */
    private static void applyLocationAttributes(IMarker marker, Location location) {
        try {
            marker.setAttribute(IMarker.LINE_NUMBER, location.getLine());
        } catch (Exception e) {
            return;
        }

        IDocument document = resolveDocument(marker);
        if (document == null) {
            // No open editor for this file (yet). Leave CHAR_START/CHAR_END unset rather than
            // writing the scanner's raw, often line-relative, start/end indices in as if they
            // were absolute file offsets - Eclipse falls back to deriving a position from
            // LINE_NUMBER alone, which is still correct for the line even without a precise range.
            return;
        }

        try {
            int line = Math.max(0, location.getLine() - 1);
            if (line >= document.getNumberOfLines()) {
                return;
            }

            IRegion lineInfo = document.getLineInformation(line);
            int lineOffset = lineInfo.getOffset();
            int lineLength = lineInfo.getLength();
            int docLength = document.getLength();

            boolean isAbsoluteOffset = location.isAbsoluteOffset();
            int charStart = isAbsoluteOffset ? location.getStartIndex() : (lineOffset + location.getStartIndex());
            int charEnd = isAbsoluteOffset ? location.getEndIndex() : (lineOffset + location.getEndIndex());

            // Scanners that don't report a real column range (e.g. ASCA only sets the line,
            // leaving start/end at their default of 0) collapse to the very start of the line here -
            // expand to the whole (leading-whitespace-trimmed) line instead of leaving a
            // zero-length position, which some Eclipse annotation-model paths treat as invalid.
            if (charStart <= lineOffset) {
                charStart = lineOffset + getLeadingWhitespaceOffset(document, lineOffset, lineLength);
            }
            if (charEnd <= charStart) {
                charEnd = lineOffset + lineLength;
            }

            charStart = Math.max(0, Math.min(charStart, docLength));
            charEnd = Math.max(charStart, Math.min(charEnd, docLength));

            marker.setAttribute(IMarker.CHAR_START, charStart);
            marker.setAttribute(IMarker.CHAR_END, charEnd);
        } catch (Exception e) {
            // Leave CHAR_START/CHAR_END unset; the LINE_NUMBER set above still positions the
            // marker on the correct line.
        }
    }

    /**
     * Finds the document for the marker's own file by searching every open editor reference
     * across all workbench windows - not just the active editor - so markers created for a
     * file that isn't currently focused (e.g. background/real-time scan results) still resolve
     * to the right document instead of silently reading whichever file happens to be active.
     * Returns null (rather than guessing) if the file has no open editor.
     */
    private static IDocument resolveDocument(IMarker marker) {
        try {
            IResource resource = marker.getResource();
            if (!(resource instanceof IFile)) {
                return null;
            }
            IFile file = (IFile) resource;

            IWorkbench workbench = PlatformUI.getWorkbench();
            if (workbench == null) {
                return null;
            }

            for (IWorkbenchWindow window : workbench.getWorkbenchWindows()) {
                IWorkbenchPage page = window.getActivePage();
                if (page == null) {
                    continue;
                }
                for (IEditorReference ref : page.getEditorReferences()) {
                    IEditorPart editorPart = ref.getEditor(false);
                    if (editorPart == null) {
                        continue;
                    }
                    IEditorInput input = editorPart.getEditorInput();
                    if (!(input instanceof IFileEditorInput)
                            || !file.equals(((IFileEditorInput) input).getFile())) {
                        continue;
                    }
                    ITextEditor textEditor = editorPart.getAdapter(ITextEditor.class);
                    if (textEditor != null) {
                        return textEditor.getDocumentProvider().getDocument(textEditor.getEditorInput());
                    }
                }
            }
        } catch (Exception e) {
            // fall through
        }
        return null;
    }

    private static int getLeadingWhitespaceOffset(IDocument document, int lineOffset, int lineLength) {
        try {
            String lineText = document.get(lineOffset, lineLength);
            int count = 0;
            while (count < lineText.length() && Character.isWhitespace(lineText.charAt(count))) {
                count++;
            }
            return count;
        } catch (Exception e) {
            return 0;
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
            case "malicious":
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
            case MALICIOUS:
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
