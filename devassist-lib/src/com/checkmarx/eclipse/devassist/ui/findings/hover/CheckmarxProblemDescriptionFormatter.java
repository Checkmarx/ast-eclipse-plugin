package com.checkmarx.eclipse.devassist.ui.findings.hover;

import com.checkmarx.eclipse.devassist.model.ScanIssue;
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
     *
     * @param issue the reconstructed scan issue
     * @return HTML fragment
     */
    public static String formatDescriptionHtml(ScanIssue issue) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div style='padding:4px;'>");

        String severity = getSeverityColor(issue.getSeverity());
        sb.append("<b style='color:").append(severity).append(";font-size:12px;'>")
          .append(HtmlEscapeUtil.escape(issue.getTitle()))
          .append("</b>");
        sb.append("<br/>");

        sb.append("<span style='color:#666;font-size:10px;'>Severity: <b>")
          .append(HtmlEscapeUtil.escape(issue.getSeverity()))
          .append("</b></span>");
        sb.append("<br/>");

        if (issue.getDescription() != null && !issue.getDescription().isEmpty()) {
            sb.append("<div style='margin:4px 0;color:#333;font-size:11px;'>")
              .append(HtmlEscapeUtil.escape(issue.getDescription()))
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
