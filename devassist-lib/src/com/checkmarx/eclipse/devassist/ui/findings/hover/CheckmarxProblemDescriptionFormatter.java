package com.checkmarx.eclipse.devassist.ui.findings.hover;

import java.util.List;

import com.checkmarx.eclipse.devassist.model.ScanEngine;
import com.checkmarx.eclipse.devassist.model.ScanIssue;
import com.checkmarx.eclipse.devassist.model.Vulnerability;
import com.checkmarx.eclipse.devassist.utils.DevAssistConstants;
import com.checkmarx.eclipse.devassist.utils.HtmlEscapeUtil;

/**
 * Formats a ScanIssue as an HTML fragment for display in the editor's line hover.
 * Mirrors the JetBrains plugin's ProblemDescription.formatDescription() technique:
 * the hover shows a read-only HTML description followed by inline text links.
 * The links are informational only (Eclipse text hovers cannot host live SWT
 * widgets) - the actual actions are performed via the Quick Fix (Ctrl+1) menu,
 * which is backed by IMarkerResolution implementations that run the real logic.
 */
public final class CheckmarxProblemDescriptionFormatter {

    private CheckmarxProblemDescriptionFormatter() {
    }

    /**
     * Build the HTML body (without outer html/body tags) describing the issue,
     * suitable for embedding inside a BrowserInformationControl or merging with
     * other annotations' hover text on the same line.
     * <p>
     * For ASCA/IAC issues that group multiple vulnerabilities on the same line,
     * renders one block per vulnerability - matching how
     * CheckmarxAnnotationHover's FindingsAnnotation branch renders the live
     * ScanIssue, so the marker-reconstructed path (this one) stays consistent
     * with it instead of collapsing back down to a single title/description.
     *
     * @param issue the reconstructed scan issue
     * @return HTML fragment
     */
    public static String formatDescriptionHtml(ScanIssue issue) {
        ScanEngine engine = issue.getScanEngine();
        boolean iterateVulnerabilities = engine == ScanEngine.ASCA || engine == ScanEngine.IAC;
        List<Vulnerability> vulnerabilities = issue.getVulnerabilities();

        if (iterateVulnerabilities && vulnerabilities != null && !vulnerabilities.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < vulnerabilities.size(); i++) {
                if (i > 0) {
                    sb.append("<hr style='margin:4px 0;border:none;border-top:1px solid #ccc;'/>");
                }
                Vulnerability vuln = vulnerabilities.get(i);
                String title = vuln.getTitle() != null && !vuln.getTitle().isEmpty() ? vuln.getTitle() : issue.getTitle();
                sb.append(formatSingleFinding(issue.getSeverity(), title, vuln.getDescription()));
            }
            return sb.toString();
        }

        return formatSingleFinding(issue.getSeverity(), issue.getTitle(), issue.getDescription());
    }

    private static String formatSingleFinding(String severity, String title, String description) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div style='padding:4px;'>");

        String severityColor = getSeverityColor(severity);
        sb.append("<b style='color:").append(severityColor).append(";font-size:12px;'>")
          .append(HtmlEscapeUtil.escape(title))
          .append("</b>");
        sb.append("<br/>");

        sb.append("<span style='color:#666;font-size:10px;'>Severity: <b>")
          .append(HtmlEscapeUtil.escape(severity))
          .append("</b></span>");
        sb.append("<br/>");

        if (description != null && !description.isEmpty()) {
            sb.append("<div style='margin:4px 0;color:#333;font-size:11px;'>")
              .append(HtmlEscapeUtil.escape(description))
              .append("</div>");
        }
        appendActionLinks(sb);
        sb.append("</div>");
        return sb.toString();
    }

    /**
     * Appends the 4 quick-fix action links (informational text, not live buttons).
     * Actual invocation happens through the Quick Fix (Ctrl+1) popup, which is
     * backed by IMarkerResolution implementations performing the same actions.
     */
    private static void appendActionLinks(StringBuilder sb) {
        sb.append("<div style='margin-top:6px;border-top:1px solid #ddd;padding-top:4px;font-size:10px;'>");
        sb.append("<span style='color:#0066cc;cursor:pointer;'>").append(HtmlEscapeUtil.escape(DevAssistConstants.FIX_WITH_DEV_ASSIST)).append("</span>");
        sb.append(" | ");
        sb.append("<span style='color:#0066cc;cursor:pointer;'>").append(HtmlEscapeUtil.escape(DevAssistConstants.VIEW_DETAILS_FIX_NAME)).append("</span>");
        sb.append(" | ");
        sb.append("<span style='color:#0066cc;cursor:pointer;'>").append(HtmlEscapeUtil.escape(DevAssistConstants.IGNORE_THIS_VULNERABILITY_FIX_NAME)).append("</span>");
        sb.append(" | ");
        sb.append("<span style='color:#0066cc;cursor:pointer;'>").append(HtmlEscapeUtil.escape(DevAssistConstants.COPY_DETAILS_FIX_NAME)).append("</span>");
        sb.append("<br/><i style='color:#999;margin-top:4px;display:block;'>Press Ctrl+1 for Quick Fix actions</i>");
        sb.append("</div>");
    }

    private static String getSeverityColor(String severity) {
        if (severity == null) {
            return "#666";
        }
        switch (severity.toLowerCase()) {
            case "malicious":
                return "#c41e3a";
            case "critical":
                return "#d63031";
            case "high":
                return "#e84c3d";
            case "medium":
                return "#f39c12";
            case "low":
                return "#27ae60";
            default:
                return "#666";
        }
    }
}
