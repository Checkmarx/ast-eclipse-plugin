package com.checkmarx.eclipse.devassist.ui.findings.dialogs;

import com.checkmarx.eclipse.devassist.model.ScanIssue;
import com.checkmarx.eclipse.devassist.model.Vulnerability;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Responsible for handling and formatting descriptions of scan issues.
 * Provides utility methods to construct and format messages for different issue types.
 * Uses HTML formatting for rich text display.
 */
public final class ProblemDescription {

    private static final String TITLE_FONT_SIZE = "font-size:11px;";
    private static final String TITLE_FONT_FAMILY = "font-family: menlo;";
    private static final String CELL_LINE_HEIGHT_STYLE = "line-height:16px;vertical-align:middle;";
    private static final String SECONDARY_SPAN_STYLE = "display:inline-block;vertical-align:middle;line-height:16px;font-size:11px;color:#ADADAD;";

    private static final String TABLE_WITH_TR = "<table style='display:inline-table;vertical-align:middle;border-collapse:collapse;'><tr>";

    /**
     * Formats a description for the given scan issue.
     *
     * @param scanIssue the ScanIssue object
     * @return formatted HTML description
     */
    public String formatDescription(ScanIssue scanIssue) {
        StringBuilder descBuilder = new StringBuilder();
        descBuilder.append("<html><body style='margin:0;padding:0;'>");

        switch (scanIssue.getScanEngine()) {
            case OSS:
                buildOSSDescription(descBuilder, scanIssue);
                break;
            case ASCA:
                buildASCADescription(descBuilder, scanIssue);
                break;
            case SECRETS:
                buildSecretsDescription(descBuilder, scanIssue);
                break;
            case IAC:
                buildIACDescription(descBuilder, scanIssue);
                break;
            case CONTAINERS:
                buildContainerDescription(descBuilder, scanIssue);
                break;
            default:
                buildDefaultDescription(descBuilder, scanIssue);
        }

        descBuilder.append("</body></html>");
        return descBuilder.toString();
    }

    private void buildOSSDescription(StringBuilder descBuilder, ScanIssue scanIssue) {
        descBuilder.append(TABLE_WITH_TR)
                .append("<td style='padding:0 6px 0 0;vertical-align:middle;'></td>")
                .append("<td style='padding:0 2px 0 2px;")
                .append(TITLE_FONT_SIZE).append(TITLE_FONT_FAMILY).append(CELL_LINE_HEIGHT_STYLE).append("'>")
                .append("<p style='margin:0;").append(TITLE_FONT_SIZE).append(TITLE_FONT_FAMILY).append("'>")
                .append("<b>").append(escapeHtml(scanIssue.getTitle())).append("@")
                .append(escapeHtml(scanIssue.getPackageVersion())).append("</b>")
                .append(" - <span style='").append(SECONDARY_SPAN_STYLE).append("'>")
                .append(escapeHtml(scanIssue.getSeverity())).append(" Risk Package")
                .append("</span></p></td></tr></table>");
        buildVulnerabilitySection(descBuilder, scanIssue);
    }

    private void buildContainerDescription(StringBuilder descBuilder, ScanIssue scanIssue) {
        descBuilder.append(TABLE_WITH_TR)
                .append("<td style='padding:0 6px 0 0;vertical-align:middle;'></td>")
                .append("<td style='padding:0 2px 0 2px;")
                .append(TITLE_FONT_SIZE).append(TITLE_FONT_FAMILY).append(CELL_LINE_HEIGHT_STYLE).append("'>")
                .append("<p style='margin:0;").append(TITLE_FONT_SIZE).append(TITLE_FONT_FAMILY).append("'>")
                .append("<b>").append(escapeHtml(scanIssue.getTitle())).append("@")
                .append(escapeHtml(scanIssue.getImageTag())).append("</b>")
                .append("</p></td></tr></table>");
        buildVulnerabilitySection(descBuilder, scanIssue);
    }

    private void buildIACDescription(StringBuilder descBuilder, ScanIssue scanIssue) {
        List<Vulnerability> vulnerabilities = scanIssue.getVulnerabilities();
        if (vulnerabilities != null) {
            for (Vulnerability vulnerability : vulnerabilities) {
                descBuilder.append(TABLE_WITH_TR)
                        .append("<td style='width:20px;padding:0 6px 0 0;vertical-align:middle;'></td>")
                        .append("<td style='padding:0 6px 0 6px;").append(TITLE_FONT_SIZE).append(TITLE_FONT_FAMILY)
                        .append(CELL_LINE_HEIGHT_STYLE).append("'>")
                        .append("<p style='").append(TITLE_FONT_SIZE).append(TITLE_FONT_FAMILY).append("'>")
                        .append("<b>").append(escapeHtml(vulnerability.getTitle())).append("</b>")
                        .append(" - ").append(escapeHtml(vulnerability.getDescription()))
                        .append(" - <span style='").append(SECONDARY_SPAN_STYLE).append("'>IaC vulnerability</span>")
                        .append("</p></td></tr></table>");
            }
        }
    }

    private void buildSecretsDescription(StringBuilder descBuilder, ScanIssue scanIssue) {
        descBuilder.append(TABLE_WITH_TR)
                .append("<td style='padding:0 6px 0 0;vertical-align:middle;'></td>")
                .append("<td style='padding:0 2px 0 2px;")
                .append(TITLE_FONT_SIZE).append(TITLE_FONT_FAMILY).append(CELL_LINE_HEIGHT_STYLE).append("'>")
                .append("<p style='margin:0;").append(TITLE_FONT_SIZE).append(TITLE_FONT_FAMILY).append("'>")
                .append("<b>").append(escapeHtml(formatTitle(scanIssue.getTitle()))).append("</b>")
                .append(" - <span style='").append(SECONDARY_SPAN_STYLE).append("'>Secret finding</span>")
                .append("</p></td></tr></table>");
    }

    private void buildASCADescription(StringBuilder descBuilder, ScanIssue scanIssue) {
        List<Vulnerability> vulnerabilities = scanIssue.getVulnerabilities();
        if (vulnerabilities != null) {
            for (Vulnerability vulnerability : vulnerabilities) {
                descBuilder.append(TABLE_WITH_TR)
                        .append("<td style='width:20px;padding:0 6px 0 0;vertical-align:middle;'></td>")
                        .append("<td style='padding:0 6px 0 6px;").append(TITLE_FONT_SIZE).append(TITLE_FONT_FAMILY)
                        .append(CELL_LINE_HEIGHT_STYLE).append("'>")
                        .append("<p style='").append(TITLE_FONT_SIZE).append(TITLE_FONT_FAMILY).append("'>")
                        .append("<b>").append(escapeHtml(vulnerability.getTitle())).append("</b>")
                        .append(" - ").append(escapeHtml(vulnerability.getDescription()))
                        .append(" - <span style='").append(SECONDARY_SPAN_STYLE).append("'>SAST vulnerability</span>")
                        .append("</p></td></tr></table>");
            }
        }
    }

    private void buildDefaultDescription(StringBuilder descBuilder, ScanIssue scanIssue) {
        descBuilder.append("<div><b>").append(escapeHtml(scanIssue.getTitle())).append("</b> -")
                .append(escapeHtml(scanIssue.getDescription())).append("</div>");
    }

    private void buildVulnerabilitySection(StringBuilder descBuilder, ScanIssue scanIssue) {
        List<Vulnerability> vulnerabilityList = scanIssue.getVulnerabilities();
        if (vulnerabilityList == null || vulnerabilityList.isEmpty()) {
            return;
        }

        descBuilder.append("<div>").append(TABLE_WITH_TR);
        Map<String, Long> vulnerabilityCount = vulnerabilityList.stream()
                .map(Vulnerability::getSeverity)
                .collect(Collectors.groupingBy(severity -> severity, Collectors.counting()));

        vulnerabilityCount.forEach((severity, count) -> {
            descBuilder.append("<td style='padding:0;'></td>")
                    .append("<td style='font-size:9px;color:#ADADAD;vertical-align:middle;padding:0 4px 0 1px;'>")
                    .append(count).append("</td>");
        });

        descBuilder.append("</tr></table></div>");
    }

    /**
     * Formats a kebab-case title into Title-Case.
     */
    private String formatTitle(String title) {
        if (title == null || title.isEmpty()) {
            return "";
        }
        return Arrays.stream(title.split("-"))
                .map(word -> word.isEmpty() ? "" : Character.toUpperCase(word.charAt(0)) + word.substring(1).toLowerCase())
                .collect(Collectors.joining("-"));
    }

    /**
     * Escape HTML special characters.
     */
    private String escapeHtml(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}

