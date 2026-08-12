package com.checkmarx.eclipse.devassist.ui.findings.hover;

import java.util.List;

import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Platform;

import com.checkmarx.eclipse.devassist.model.ScanEngine;
import com.checkmarx.eclipse.devassist.model.ScanIssue;
import com.checkmarx.eclipse.devassist.model.Vulnerability;
import com.checkmarx.eclipse.devassist.utils.DevAssistConstants;
import com.checkmarx.eclipse.devassist.utils.HtmlEscapeUtil;

/**
 * Formats a ScanIssue as an HTML fragment for display in the editor's line hover.
 * Consolidates all HTML rendering for both marker-based and live FindingsAnnotation paths.
 * <p>
 * Supports two action link modes:
 * - Clickable (enableClickableActions=true): renders <a href='#action:...'> for LocationListener interception
 * - Informational (enableClickableActions=false): renders plain text with Ctrl+1 hint
 * <p>
 * For ASCA/IAC issues that group multiple vulnerabilities on the same line,
 * renders one block per vulnerability instead of collapsing to root attributes.
 */
public final class CheckmarxProblemDescriptionFormatter {

    private CheckmarxProblemDescriptionFormatter() {
    }

    /**
     * Build the HTML body (without outer html/body tags) describing the issue,
     * suitable for embedding inside a BrowserInformationControl or merging with
     * other annotations' hover text on the same line.
     * <p>
     * Supports both clickable action links (for CheckmarxAnnotationHover's BrowserInformationControl)
     * and informational-only links (for marker resolution fallback).
     *
     * @param issue the scan issue
     * @param enableClickableActions if true, renders action links as #action:... for LocationListener interception;
     *                               if false, renders as informational text with Ctrl+1 hint
     * @return HTML fragment
     */
    public static String formatDescriptionHtml(ScanIssue issue, boolean enableClickableActions) {
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
                sb.append(formatSingleFinding(issue.getSeverity(), title, vuln.getDescription(), enableClickableActions));
            }
            return sb.toString();
        }

        return formatSingleFinding(issue.getSeverity(), issue.getTitle(), issue.getDescription(), enableClickableActions);
    }

    /**
     * Legacy overload for backward compatibility: defaults to informational action links (non-clickable).
     */
    public static String formatDescriptionHtml(ScanIssue issue) {
        return formatDescriptionHtml(issue, false);
    }

    private static String formatSingleFinding(String severity, String title, String description, boolean enableClickableActions) {
        StringBuilder sb = new StringBuilder();

        // Get severity icon and color
        String severityIcon = getSeverityIconHtml(severity);
        String severityColor = getSeverityColor(severity);

        // Use JetBrains-style table layout: icon (left) | content (right)
        sb.append(InlineStyle.TABLE_WITH_TR);

        // First column: severity icon (20px wide)
        if (!severityIcon.isEmpty()) {
            sb.append("<td style='").append(InlineStyle.ICON_COLUMN_STYLE).append("'>")
              .append(severityIcon)
              .append("</td>");
        }

        // Second column: content
        sb.append("<td style='").append(InlineStyle.CONTENT_COLUMN_STYLE).append("'>");

        // Title (bold, colored)
        sb.append("<div style='display:block;word-break:break-word;overflow-wrap:anywhere;margin-bottom:4px;'>");
        sb.append("<b style='color:").append(severityColor).append(";font-size:12px;'>")
          .append(HtmlEscapeUtil.escape(title))
          .append("</b>");

        // Severity label (secondary text)
        sb.append(" <span style='").append(InlineStyle.SECONDARY_SPAN_STYLE).append("'>")
          .append(HtmlEscapeUtil.escape(severity))
          .append("</span>");
        sb.append("</div>");

        // Description
        if (description != null && !description.isEmpty()) {
            sb.append("<div style='margin:4px 0;color:#333;font-size:11px;'>")
              .append(HtmlEscapeUtil.escape(description))
              .append("</div>");
        }

        sb.append("</td>");
        sb.append("</tr></table>");

        // Action links (outside table)
        appendActionLinks(sb, enableClickableActions);

        return sb.toString();
    }

    /**
     * Appends action links in two modes:
     * - Clickable mode (enableClickableActions=true): renders <a href='#action:...'>  links for LocationListener interception
     * - Informational mode (enableClickableActions=false): renders plain text with Ctrl+1 hint
     */
    private static void appendActionLinks(StringBuilder sb, boolean enableClickableActions) {
        sb.append("<div style='margin-top:6px;border-top:1px solid #ddd;padding-top:4px;font-size:10px;'>");

        if (enableClickableActions) {
            // Render as clickable action links (for BrowserInformationControl with LocationListener)
            sb.append("<a href='#action:fix' style='color:#0066cc;text-decoration:underline;cursor:pointer;margin-right:8px;'>")
              .append(HtmlEscapeUtil.escape(DevAssistConstants.FIX_WITH_DEV_ASSIST))
              .append("</a>");
            sb.append(" | ");
            sb.append("<a href='#action:details' style='color:#0066cc;text-decoration:underline;cursor:pointer;margin-right:8px;'>")
              .append(HtmlEscapeUtil.escape(DevAssistConstants.VIEW_DETAILS_FIX_NAME))
              .append("</a>");
            sb.append(" | ");
            sb.append("<a href='#action:ignore' style='color:#0066cc;text-decoration:underline;cursor:pointer;margin-right:8px;'>")
              .append(HtmlEscapeUtil.escape(DevAssistConstants.IGNORE_THIS_VULNERABILITY_FIX_NAME))
              .append("</a>");
            sb.append(" | ");
            sb.append("<a href='#action:copy' style='color:#0066cc;text-decoration:underline;cursor:pointer;'>")
              .append(HtmlEscapeUtil.escape(DevAssistConstants.COPY_DETAILS_FIX_NAME))
              .append("</a>");
        } else {
            // Render as informational text (for marker resolution fallback)
            sb.append("<span style='color:#0066cc;cursor:pointer;'>").append(HtmlEscapeUtil.escape(DevAssistConstants.FIX_WITH_DEV_ASSIST)).append("</span>");
            sb.append(" | ");
            sb.append("<span style='color:#0066cc;cursor:pointer;'>").append(HtmlEscapeUtil.escape(DevAssistConstants.VIEW_DETAILS_FIX_NAME)).append("</span>");
            sb.append(" | ");
            sb.append("<span style='color:#0066cc;cursor:pointer;'>").append(HtmlEscapeUtil.escape(DevAssistConstants.IGNORE_THIS_VULNERABILITY_FIX_NAME)).append("</span>");
            sb.append(" | ");
            sb.append("<span style='color:#0066cc;cursor:pointer;'>").append(HtmlEscapeUtil.escape(DevAssistConstants.COPY_DETAILS_FIX_NAME)).append("</span>");
            sb.append("<br/><i style='color:#999;margin-top:4px;display:block;'>Press Ctrl+1 for Quick Fix actions</i>");
        }

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

    /**
     * Get HTML img tag for severity icon (16x16).
     * Mirrors JetBrains' getStyledImage() approach using icon resources.
     */
    private static String getSeverityIconHtml(String severity) {
        if (severity == null || severity.isEmpty()) {
            return "";
        }

        try {
            String iconName = severity.toLowerCase() + "_16.svg";
            org.eclipse.core.runtime.IPath iconPath = new Path("icons/severity/" + iconName);

            // Try to load from main plugin bundle first (checkmarx-ast-eclipse-plugin)
            org.osgi.framework.Bundle bundle = Platform.getBundle("com.checkmarx.eclipse.plugin");
            if (bundle != null) {
                java.net.URL iconUrl = FileLocator.find(bundle, iconPath, null);
                if (iconUrl != null) {
                    java.net.URL fileUrl = FileLocator.toFileURL(iconUrl);
                    String urlString = fileUrl.toString();
                    // Apply inline styles matching JetBrains' ICON_INLINE_STYLE
                    return "<img src='" + urlString + "' style='display:inline-block;vertical-align:middle;max-height:16px;line-height:16px;' width='16' height='16'/>";
                }
            }
        } catch (Exception e) {
            // If icon loading fails, return empty and rendering continues without icon
        }

        return "";
    }

    /**
     * Inline styles matching JetBrains' ProblemDescription.InlineStyle.
     * Ensures visual consistency with JetBrains plugin design.
     */
    static class InlineStyle {

        private InlineStyle() {
        }

        // Table layout: icon (20px) in first column, content in second column
        static final String TABLE_WITH_TR =
                "<table style='display:inline-table;vertical-align:middle;border-collapse:collapse;'><tr>";

        static final String TABLE_WITH_TR_FULL_WIDTH =
                "<table cellspacing='0' cellpadding='0' " +
                "style='display:table;" +
                "border-collapse:collapse;" +
                "table-layout:fixed;" +
                "width:460px;'><tr>";

        // Typography styles
        static final String TITLE_FONT_FAMILY = "font-family: menlo;";
        static final String TITLE_FONT_SIZE = "font-size:11px;";
        static final String CELL_LINE_HEIGHT_STYLE = "line-height:16px;vertical-align:middle;";

        // Secondary text (severity labels like "SAST vulnerability", "IaC vulnerability")
        static final String SECONDARY_SPAN_STYLE =
                "display:inline-block;vertical-align:middle;line-height:16px;font-size:11px;color:#ADADAD;"
                        + "font-family:system-ui, -apple-system, 'Segoe UI', Roboto, Arial, sans-serif;";

        // Icon styling - keep icons consistent size
        static final String ICON_INLINE_STYLE =
                "display:inline-block;vertical-align:middle;max-height:16px;line-height:16px;";

        // Icon column style (20px wide, right-padded)
        static final String ICON_COLUMN_STYLE = "width:20px;padding:0 6px 0 0;vertical-align:middle;";

        // Content column style
        static final String CONTENT_COLUMN_STYLE = "padding:0 4px;white-space:normal;" + TITLE_FONT_SIZE + TITLE_FONT_FAMILY + CELL_LINE_HEIGHT_STYLE;
    }
}
