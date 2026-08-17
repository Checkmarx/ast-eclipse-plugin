package com.checkmarx.eclipse.devassist.ui.findings.hover;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.eclipse.core.runtime.FileLocator;
import java.net.URL;
import java.util.Arrays;
import com.checkmarx.eclipse.devassist.backend.SeverityLevel;
import com.checkmarx.eclipse.devassist.model.ScanEngine;
import com.checkmarx.eclipse.devassist.model.ScanIssue;
import com.checkmarx.eclipse.devassist.model.Vulnerability;
import com.checkmarx.eclipse.devassist.utils.DevAssistConstants;
import com.checkmarx.eclipse.devassist.utils.DevAssistUtils;
import com.checkmarx.eclipse.devassist.utils.HtmlEscapeUtil;
import static com.checkmarx.eclipse.devassist.ui.findings.hover.CheckmarxProblemDescriptionFormatter.InlineStyle.*;
import static com.checkmarx.eclipse.devassist.utils.DevAssistConstants.SEPERATOR;

/**
 * Formats a ScanIssue as an HTML fragment for display in the editor's line
 * hover. Consolidates all HTML rendering for both marker-based and live
 * FindingsAnnotation paths.
 * <p>
 * Supports two action link modes: - Clickable (enableClickableActions=true):
 * renders <a href='#action:...'> for LocationListener interception -
 * Informational (enableClickableActions=false): renders plain text with Ctrl+1
 * hint
 * <p>
 * For ASCA/IAC issues that group multiple vulnerabilities on the same line,
 * renders one block per vulnerability instead of collapsing to root attributes.
 */
public final class CheckmarxProblemDescriptionFormatter {

	private static final Map<String, String> DESCRIPTION_ICON = new LinkedHashMap<>();

	private static final String COUNT = "COUNT";
	private static final String PACKAGE = "Package";
	private static final String DEV_ASSIST = "DevAssist";
	private static final String CONTAINER = "Container";

	public CheckmarxProblemDescriptionFormatter() {
		initIconsMap();
	}

	private static void initIconsMap() {
		DESCRIPTION_ICON.put(SeverityLevel.MALICIOUS.getSeverity(),
				getImage(DevAssistConstants.ImagePaths.MALICIOUS_PNG));
		DESCRIPTION_ICON.put(SeverityLevel.CRITICAL.getSeverity(),
				getImage(DevAssistConstants.ImagePaths.CRITICAL_PNG));
		DESCRIPTION_ICON.put(SeverityLevel.HIGH.getSeverity(), getImage(DevAssistConstants.ImagePaths.HIGH_PNG));
		DESCRIPTION_ICON.put(SeverityLevel.MEDIUM.getSeverity(), getImage(DevAssistConstants.ImagePaths.MEDIUM_PNG));
		DESCRIPTION_ICON.put(SeverityLevel.LOW.getSeverity(), getImage(DevAssistConstants.ImagePaths.LOW_PNG));

		DESCRIPTION_ICON.put(getSeverityCountIconKey(SeverityLevel.CRITICAL.getSeverity()),
				getImage(DevAssistConstants.ImagePaths.CRITICAL_16_PNG));
		DESCRIPTION_ICON.put(getSeverityCountIconKey(SeverityLevel.HIGH.getSeverity()),
				getImage(DevAssistConstants.ImagePaths.HIGH_16_PNG));
		DESCRIPTION_ICON.put(getSeverityCountIconKey(SeverityLevel.MEDIUM.getSeverity()),
				getImage(DevAssistConstants.ImagePaths.MEDIUM_16_PNG));
		DESCRIPTION_ICON.put(getSeverityCountIconKey(SeverityLevel.LOW.getSeverity()),
				getImage(DevAssistConstants.ImagePaths.LOW_16_PNG));

		DESCRIPTION_ICON.put(PACKAGE, getImage(DevAssistConstants.ImagePaths.PACKAGE_PNG));
		DESCRIPTION_ICON.put(DEV_ASSIST, getImage(DevAssistConstants.ImagePaths.DEV_ASSIST_PNG));
		DESCRIPTION_ICON.put(CONTAINER, getImage(DevAssistConstants.ImagePaths.CONTAINER_PNG));
	}

	/**
	 * Build the HTML body (without outer html/body tags) describing the issue,
	 * suitable for embedding inside a BrowserInformationControl or merging with
	 * other annotations' hover text on the same line.
	 * <p>
	 * Supports both clickable action links (for CheckmarxAnnotationHover's
	 * BrowserInformationControl) and informational-only links (for marker
	 * resolution fallback).
	 *
	 * @param issue                  the scan issue
	 * @param enableClickableActions if true, renders action links as #action:...
	 *                               for LocationListener interception; if false,
	 *                               renders as informational text with Ctrl+1 hint
	 * @param textColor              text color in hex format (e.g., "#000000" for dark themes,
	 *                               "#FFFFFF" for light), or null to use inherited color
	 * @return HTML fragment
	 */
	public String formatDescriptionHtml(ScanIssue scanIssue, boolean enableClickableActions, String textColor) {
		StringBuilder descBuilder = new StringBuilder();

		// DevAssist image
		descBuilder.append(TABLE_WITH_TR).append("<td style='vertical-align:middle;'>")
				.append(DESCRIPTION_ICON.get(DEV_ASSIST)).append("</td></tr></table>");
		descBuilder.append("<hr style='margin:4px 0;border:none;border-top:none solid #ccc;'/>");

		// For ASCA and IAC multiple violations
		appendMultipleViolationsTitle(descBuilder, scanIssue, textColor);

		switch (scanIssue.getScanEngine()) {
		case OSS:
			buildOSSDescription(descBuilder, scanIssue, textColor);
			break;
		case ASCA:
			buildASCADescription(descBuilder, scanIssue, textColor);
			break;
		case SECRETS:
			buildSecretsDescription(descBuilder, scanIssue, textColor);
			break;
		case IAC:
			buildIACDescription(descBuilder, scanIssue, textColor);
			break;
		case CONTAINERS:
			buildContainerDescription(descBuilder, scanIssue);
			break;
		default:
			buildDefaultDescription(descBuilder, scanIssue);
		}
		if (scanIssue.getScanEngine() != ScanEngine.IAC && scanIssue.getScanEngine() != ScanEngine.ASCA) {
			buildRemediationActionsSection(descBuilder, scanIssue.getScanIssueId(), scanIssue.getScanEngine().name());
		}
		return descBuilder.toString();
	}

	/**
	 * Builds the OSS description for the provided scan issue and appends it to the
	 * given StringBuilder. This method incorporates severity-specific formatting,
	 * including handling for malicious packages, and assembles the description with
	 * the package header and vulnerability details.
	 *
	 * @param descBuilder the StringBuilder to which the formatted OSS description
	 *                    will be appended
	 * @param scanIssue   the ScanIssue object containing information about the
	 *                    scanned issue, including its severity, vulnerabilities,
	 *                    and related details
	 */
	private void buildOSSDescription(StringBuilder descBuilder, ScanIssue scanIssue, String textColor) {
		buildPackageMessage(descBuilder, scanIssue, textColor);
		buildVulnerabilitySection(descBuilder, scanIssue);
	}

	/**
	 * Builds the package header section of a description for a scan issue and
	 * appends it to the provided StringBuilder. This method formats information
	 * about the scan issue's severity, title, and package version, and includes an
	 * associated image icon representing the issue.
	 *
	 * @param descBuilder the StringBuilder to which the formatted package header
	 *                    information will be appended
	 * @param scanIssue   the ScanIssue object containing details about the issue
	 *                    such as severity, title, and package version
	 */
	private static void buildPackageMessage(StringBuilder descBuilder, ScanIssue scanIssue, String textColor) {
		String secondaryText = DevAssistConstants.SEVERITY_PACKAGE;
		String colorStyle = textColor != null && !textColor.isEmpty() ? "color:" + textColor + ";" : "";
		String iconKey = PACKAGE;
		if (scanIssue.getSeverity().equalsIgnoreCase(SeverityLevel.MALICIOUS.getSeverity())) {
			secondaryText = PACKAGE;
			iconKey = scanIssue.getSeverity();
		}
		String icon = getSeverityIconHtml(iconKey, ICON_INLINE_STYLE);

		descBuilder.append(TABLE_WITH_TR).append("<td style='padding:0 6px 0 0;vertical-align:middle;'>").append(icon)
				.append("</td>").append("<td style=' " + colorStyle + " padding:0 2px 0 2px;").append(TITLE_FONT_SIZE)
				.append(TITLE_FONT_FAMILY).append(CELL_LINE_HEIGHT_STYLE).append("'>").append("<p style='margin:0;")
				.append(TITLE_FONT_SIZE).append(TITLE_FONT_FAMILY).append("'>").append("<b>")
				.append(HtmlEscapeUtil.escape(scanIssue.getTitle())).append("@")
				.append(HtmlEscapeUtil.escape(scanIssue.getPackageVersion())).append("</b>").append(" - <span style='")
				.append(SECONDARY_SPAN_STYLE).append("'>").append(HtmlEscapeUtil.escape(scanIssue.getSeverity()))
				.append(" ").append(HtmlEscapeUtil.escape(secondaryText)).append("</span></p></td></tr></table>");
	}

	/**
	 * Builds the vulnerability section of a scan issue description and appends it
	 * to the provided StringBuilder. This method processes the list of
	 * vulnerabilities associated with the scan issue, categorizes them by severity,
	 * and includes detailed descriptions for specific vulnerabilities where
	 * applicable.
	 *
	 * @param descBuilder the StringBuilder to which the formatted vulnerability
	 *                    section will be appended
	 * @param scanIssue   the ScanIssue object containing details about the scan,
	 *                    including associated vulnerabilities
	 */
	private void buildVulnerabilitySection(StringBuilder descBuilder, ScanIssue scanIssue) {
		List<Vulnerability> vulnerabilityList = scanIssue.getVulnerabilities();
		if (vulnerabilityList == null || vulnerabilityList.isEmpty()) {
			return;
		}
		descBuilder.append("<div>").append(TABLE_WITH_TR);
		Map<String, Long> vulnerabilityCount = getVulnerabilityCount(vulnerabilityList);
		DESCRIPTION_ICON.forEach((severity, iconPath) -> {
			Long count = vulnerabilityCount.get(severity);
			if (count != null && count > 0) {
				descBuilder.append("<td style='padding:4px 1px 0 0;'>")
						.append(DESCRIPTION_ICON.get(getSeverityCountIconKey(severity))).append("</td>")
						.append("<td style='font-size:10px;color:#A0A0A0;vertical-align:middle;padding:2px 4px 0 1px;'>")
						.append(count).append("</td>");
			}
		});
		descBuilder.append("</tr></table></div>");
	}

	/**
	 * ASCA description. Format: [Title for multiple issues] [Severity Icon] Title
	 * (bold) - description - SAST vulnerability
	 */
	private void buildASCADescription(StringBuilder descBuilder, ScanIssue scanIssue, String textColor) {
		for (Vulnerability vulnerability : scanIssue.getVulnerabilities()) {
			String severityIcon = getSeverityIconHtml(vulnerability.getSeverity(), ICON_INLINE_STYLE);
			descBuilder.append(TABLE_WITH_TR_IAC_ASCA)
					.append("<td style='width:20px;padding:0 6px 0 0;vertical-align:middle;'>").append(severityIcon)
					.append("</td>");
			String colorStyle = textColor != null && !textColor.isEmpty() ? "color:" + textColor + ";" : "";
			descBuilder.append("<td style='padding:0 6px 0 6px;").append(TITLE_FONT_SIZE).append(TITLE_FONT_FAMILY)
					.append(CELL_LINE_HEIGHT_STYLE).append("'>")
					.append("<div style='display:flex;flex-direction:row;align-items:center;gap:6px;'>")
					.append("<p style=\"").append(colorStyle).append(TITLE_FONT_SIZE).append(TITLE_FONT_FAMILY).append("\">").append("<b>")
					.append(HtmlEscapeUtil.escape(vulnerability.getTitle())).append("</b>").append(" - ")
					.append(HtmlEscapeUtil.escape(vulnerability.getDescription())).append(" - <span style='")
					.append(SECONDARY_SPAN_STYLE).append("'>SAST vulnerability</span>").append("</p>")
					.append("</div></td></tr></table>");
			 buildRemediationActionsSection(descBuilder, vulnerability.getVulnerabilityId(), scanIssue.getScanEngine().name());
		}
	}
	

	/**
	 * Secrets description. Format: [Severity Icon] Title (bold) - Secret finding
	 */
	private void buildSecretsDescription(StringBuilder descBuilder, ScanIssue scanIssue, String textColor) {
		String icon = getSeverityIconHtml(scanIssue.getSeverity(), ICON_INLINE_STYLE);
		String colorStyle = textColor != null && !textColor.isEmpty() ? "color:" + textColor + ";" : "";
		descBuilder.append(TABLE_WITH_TR).append("<td style='padding:0 6px 0 0;vertical-align:middle;'>").append(icon)
				.append("</td>").append("<td style='" + colorStyle + "padding:0 2px 0 2px;").append(TITLE_FONT_SIZE)
				.append(TITLE_FONT_FAMILY).append(CELL_LINE_HEIGHT_STYLE).append("'>").append("<p style='margin:0;")
				.append(TITLE_FONT_SIZE).append(TITLE_FONT_FAMILY).append("'>").append("<b>")
				.append(HtmlEscapeUtil.escape(formatTitle(scanIssue.getTitle()))).append("</b>")
				.append(" - <span style='").append(SECONDARY_SPAN_STYLE).append("'>Secret finding</span>")
				.append("</p></td></tr></table>");
	}

	/**
	 * IAC description (image header + vulnerability description with Title).
	 */
	private void buildIACDescription(StringBuilder descBuilder, ScanIssue scanIssue, String textColor) {
		for (Vulnerability vulnerability : scanIssue.getVulnerabilities()) {
			String severityIcon = getSeverityIconHtml(vulnerability.getSeverity(), ICON_INLINE_STYLE);
			descBuilder.append(TABLE_WITH_TR_IAC_ASCA)
					.append("<td style='width:20px;padding:0 6px 0 0;vertical-align:middle;'>").append(severityIcon)
					.append("</td>");
			String colorStyle = textColor != null && !textColor.isEmpty() ? "color:" + textColor + ";" : "";
			descBuilder
					.append("<td style='" + colorStyle + "padding:0 4px;" + "white-space:normal;" + TITLE_FONT_SIZE
							+ TITLE_FONT_FAMILY + CELL_LINE_HEIGHT_STYLE + "'>")
					.append("<div style='" + colorStyle + "display:block;" + "word-break:break-word;"
							+ "overflow-wrap:anywhere;" + "'>")
					.append("<b>").append(HtmlEscapeUtil.escape(vulnerability.getTitle())).append("</b>").append(" - ")
					.append(HtmlEscapeUtil.escape(vulnerability.getActualValue())).append(" ")
					.append(HtmlEscapeUtil.escape(vulnerability.getDescription()))
					.append(" <span style='" + TITLE_FONT_SIZE + TITLE_FONT_FAMILY + CELL_LINE_HEIGHT_STYLE)
					.append(SECONDARY_SPAN_STYLE).append("'>  IaC vulnerability</span>")
					.append("</div></td></tr></table>");
			buildRemediationActionsSection(descBuilder, vulnerability.getVulnerabilityId(),
					scanIssue.getScanEngine().name());
		}
	}

	/**
	 * Container description (image header + vulnerability counts).
	 */
	private void buildContainerDescription(StringBuilder descBuilder, ScanIssue scanIssue) {
		buildImageHeader(descBuilder, scanIssue);
		buildVulnerabilitySection(descBuilder, scanIssue);
	}

	/**
	 * Builds the default description for a scan issue and appends it to the
	 * provided StringBuilder. This method formats basic details about the scan
	 * issue, including its title and description.
	 *
	 * @param descBuilder the StringBuilder to which the formatted default
	 *                    description will be appended
	 * @param scanIssue   the ScanIssue object containing details about the issue
	 *                    such as title and description
	 */
	/**
	 * Default fallback description.
	 */
	private void buildDefaultDescription(StringBuilder descBuilder, ScanIssue scanIssue) {
		descBuilder.append("<div><b>").append(scanIssue.getTitle()).append("</b> -").append(scanIssue.getDescription());
	}

	/**
	 * Container image header.
	 */
	private void buildImageHeader(StringBuilder descBuilder, ScanIssue scanIssue) {
		String icon = getSeverityIconHtml(CONTAINER, ICON_INLINE_STYLE);

		descBuilder.append(TABLE_WITH_TR).append("<td style='padding:0 6px 0 0;vertical-align:middle;'>").append(icon)
				.append("</td>").append("<td style='padding:0 2px 0 2px;").append(TITLE_FONT_SIZE)
				.append(TITLE_FONT_FAMILY).append(CELL_LINE_HEIGHT_STYLE).append("'>").append("<p style='margin:0;")
				.append(TITLE_FONT_SIZE).append(TITLE_FONT_FAMILY).append("'>").append("<b>")
				.append(HtmlEscapeUtil.escape(scanIssue.getTitle())).append("@")
				.append(HtmlEscapeUtil.escape(scanIssue.getImageTag())).append("</b>").append("</p></td></tr></table>");
	}

	/**
	 * Calculates the count of vulnerabilities grouped by their severity levels.
	 * This method processes a list of vulnerabilities, retrieves their severity,
	 * and returns a map where the keys are severity levels and the values are the
	 * counts.
	 *
	 * @param vulnerabilityList the list of vulnerabilities to be grouped and
	 *                          counted by severity
	 * @return a map where the key is the severity level and the value is the count
	 *         of vulnerabilities at that severity
	 */
	private Map<String, Long> getVulnerabilityCount(List<Vulnerability> vulnerabilityList) {
		return vulnerabilityList.stream().map(Vulnerability::getSeverity)
				.collect(Collectors.groupingBy(severity -> severity, Collectors.counting()));
	}

	/**
	 * Legacy overload for backward compatibility: defaults to informational action
	 * links (non-clickable) and no text color override.
	 */
	public String formatDescriptionHtml(ScanIssue issue) {
		return formatDescriptionHtml(issue, false, null);
	}

	
	/**
	 * Builds the remediation actions section of the description.
	 *
	 * @param descBuilder {@link StringBuilder} object to add the remediation
	 *                    actions section to.
	 * @param scanIssueId {@link String} object containing the remediation actions
	 *                    section data.
	 */
	private void buildRemediationActionsSection(StringBuilder descBuilder, String scanIssueId, String engineName) {
		String buttonStyle = "text-decoration: none; color: #4470EC; " + TITLE_FONT_SIZE + TITLE_FONT_FAMILY
				+ CELL_LINE_HEIGHT_STYLE + "white-space: nowrap; margin:0; padding:0;";
		descBuilder.append(
				"<table style='display:block;margin:8px 0 0 0;border-collapse:collapse;border-spacing:0;padding:0;'><tr>")
				.append("<td style='padding:0 10px 0 0;margin:0;'>").append("<a href=\"#cxonedevassist/copyfixprompt")
				.append(SEPERATOR).append(scanIssueId).append(SEPERATOR).append(engineName).append("\" style='")
				.append(buttonStyle).append(TITLE_FONT_FAMILY).append("'>")
				.append(DevAssistUtils.getAssistQuickFixName()).append("</a></td>")
				.append("<td style='padding:0 10px 0 0;margin:0;'>").append("<a href=\"#cxonedevassist/viewdetails")
				.append(SEPERATOR).append(scanIssueId).append(SEPERATOR).append("\" style='").append(buttonStyle)
				.append(TITLE_FONT_FAMILY).append("'>").append(DevAssistConstants.VIEW_DETAILS_FIX_NAME)
				.append("</a></td>").append("<td style='padding:0 10px 0 0;margin:0;'>")
				.append("<a href=\"#cxonedevassist/ignorethis").append(SEPERATOR).append(scanIssueId).append(SEPERATOR)
				.append("\" style='").append(buttonStyle).append(TITLE_FONT_FAMILY).append("'>")
				.append(DevAssistConstants.IGNORE_THIS_VULNERABILITY_FIX_NAME).append("</a></td>");
		if (engineName.equalsIgnoreCase(String.valueOf(ScanEngine.OSS))
				|| engineName.equalsIgnoreCase(String.valueOf(ScanEngine.CONTAINERS))) {
			descBuilder.append("<td style='padding:0 5px 0 0;margin:0;'>")
					.append("<a href=\"#cxonedevassist/ignoreallofthis").append(SEPERATOR).append(scanIssueId)
					.append(SEPERATOR).append("\" style='").append(buttonStyle).append(TITLE_FONT_FAMILY).append("'>")

					.append(DevAssistConstants.IGNORE_ALL_OF_THIS_TYPE_FIX_NAME);
		}
		descBuilder.append("</a></td>").append("</tr></table><br>");
	}


	/**
	 * Injects inline styles into an existing HTML image tag.
	 */
	private static String getSeverityIconHtml(String key, String extraStyle) {
		String imgTag = DESCRIPTION_ICON.getOrDefault(key, "");

		if (imgTag == null || imgTag.isEmpty()) {
			return "";
		}

		if (imgTag.contains("style='")) {
			return imgTag.replaceFirst("style='", "style='" + extraStyle);
		} else if (imgTag.contains("style=\"")) {
			return imgTag.replaceFirst("style=\"", "style=\"" + extraStyle);
		} else {
			int insertPos = imgTag.indexOf("/>");

			return insertPos > 0
					? imgTag.substring(0, insertPos) + " style='" + extraStyle + "'" + imgTag.substring(insertPos)
					: imgTag;
		}
	}

	/**
	 * Inline styles matching JetBrains' ProblemDescription.InlineStyle. Ensures
	 * visual consistency with JetBrains plugin design.
	 */
	static class InlineStyle {

		private InlineStyle() {
		}

		// Table layout: icon (20px) in first column, content in second column
		static final String TABLE_WITH_TR = "<table style='display:inline-table;vertical-align:middle;border-collapse:collapse;'><tr>";
		static final String TABLE_WITH_TR_IAC_ASCA = "<table cellspacing='0' cellpadding='0' " + "style='display:table;"
				+ "border-collapse:collapse;" + "table-layout:fixed;" + "width:460px;'><tr>";

		static final String TABLE_WITH_TR_FULL_WIDTH = "<table cellspacing='0' cellpadding='0' "
				+ "style='display:table;" + "border-collapse:collapse;" + "table-layout:fixed;" + "width:460px;'><tr>";

		// Typography styles
		static final String TITLE_FONT_FAMILY = "font-family: sans-serif";
		static final String TITLE_FONT_SIZE = "font-size:12px;";
		static final String CELL_LINE_HEIGHT_STYLE = "line-height:16px;vertical-align:middle;";

		// Secondary text (severity labels like "SAST vulnerability", "IaC
		// vulnerability")
		static final String SECONDARY_SPAN_STYLE = "display:inline-block;vertical-align:middle;line-height:16px;font-size:11px;color:#ADADAD;"
				+ "font-family:system-ui, -apple-system, 'Segoe UI', Roboto, Arial, sans-serif;";

		// Icon column style (20px wide, right-padded)
		static final String ICON_COLUMN_STYLE = "width:20px;padding:0 6px 0 0;vertical-align:middle;";

		// Content column style
		static final String CONTENT_COLUMN_STYLE = "padding:0 4px;white-space:normal;" + TITLE_FONT_SIZE
				+ TITLE_FONT_FAMILY + CELL_LINE_HEIGHT_STYLE;

		/**
		 * Default inline severity icon style used consistently across all engines.
		 */
		static final String ICON_INLINE_STYLE = "display:inline-block;vertical-align:middle;max-height:16px;line-height:16px;";
	}

	/**
	 * Appends multiple violations title for ASCA and IAC engines when there are
	 * multiple vulnerabilities. This method adds a formatted title showing the
	 * number of violations detected.
	 *
	 * @param descBuilder the StringBuilder to append the title to
	 * @param scanIssue   the ScanIssue containing information about vulnerabilities
	 */
	private static void appendMultipleViolationsTitle(StringBuilder descBuilder, ScanIssue scanIssue, String textColor) {
		String colorStyle = textColor != null && !textColor.isEmpty() ? "color:" + textColor + ";" : "";
		if (scanIssue.getVulnerabilities() == null || scanIssue.getVulnerabilities().size() <= 1) {
			return;
		}
		boolean isASCAOrIAC = scanIssue.getScanEngine() == ScanEngine.ASCA
				|| scanIssue.getScanEngine() == ScanEngine.IAC;
		if (isASCAOrIAC) {
			descBuilder.append(TABLE_WITH_TR).append("<td style='" + colorStyle + "padding:0 6px 0 0;'>").append("<p style='margin:0;")
					.append(TITLE_FONT_SIZE).append(TITLE_FONT_FAMILY).append("'>")
					.append(HtmlEscapeUtil.escape(scanIssue.getTitle())).append(" <span style='")
					.append(SECONDARY_SPAN_STYLE).append("'>Checkmarx One Assist</span>")
					.append("</p></td></tr></table>");
		}
	}

	/**
	 * Generates an HTML image element based on the provided icon name.
	 *
	 * @param iconPath the path to the image file that will be used in the HTML
	 *                 content
	 * @return a String representing an HTML image element with the provided icon
	 *         path
	 */
	private static String getImage(String iconPath) {
		String imagePath = DevAssistUtils.themeBasedPNGIconForHtmlImage(iconPath);
		if (imagePath == null || imagePath.isEmpty()) {
			return "";
		}
		try {
			URL imageUrl = new URL(imagePath);
			if (imageUrl != null) {
				URL fileUrl = FileLocator.toFileURL(imageUrl);
				String urlString = fileUrl.toString();
				return "<img src='" + urlString + "' " + "style='display:inline-block;vertical-align:middle;' />";
			}
		} catch (Exception e) {
			return "";
		}
		return "";
	}

	/**
	 * Formats a kebab-case title into Title-Case (e.g., "generic-api-key" ->
	 * "Generic-Api-Key").
	 *
	 * @param title The kebab-case title string.
	 * @return A formatted Title-Case string.
	 */
	private String formatTitle(String title) {
		if (title == null || title.isEmpty()) {
			return "";
		}
		return Arrays.stream(title.split("-")).map(
				word -> word.isEmpty() ? "" : Character.toUpperCase(word.charAt(0)) + word.substring(1).toLowerCase())
				.collect(Collectors.joining("-"));
	}

	/**
	 * Returns the key for the icon representing the specified severity with a count
	 * suffix.
	 *
	 * @param severity the severity
	 * @return the key for the icon representing the specified severity with a count
	 *         suffix
	 */
	private static String getSeverityCountIconKey(String severity) {
		return severity + COUNT;
	}

}
