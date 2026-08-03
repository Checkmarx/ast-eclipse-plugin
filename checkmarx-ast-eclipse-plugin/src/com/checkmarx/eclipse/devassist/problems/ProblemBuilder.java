package com.checkmarx.eclipse.devassist.problems;

import java.util.ArrayList;
import java.util.List;

import com.checkmarx.eclipse.devassist.model.ScanIssue;

/**
 * Static factory for creating ProblemDescriptor objects.
 *
 * Encapsulates logic for:
 * - Formatting problem descriptions
 * - Creating appropriate fixes for issues
 * - Building ProblemDescriptor instances
 *
 * Mirrors JetBrains ProblemBuilder.
 * Cannot be instantiated.
 */
public final class ProblemBuilder {

	private ProblemBuilder() {
	}

	/**
	 * Build a ProblemDescriptor from a scan issue.
	 *
	 * Mirrors JetBrains ProblemBuilder.build().
	 *
	 * @param problemHelper Context with file, document, etc.
	 * @param scanIssue The scan issue to describe
	 * @param problemLineNumber Line number where problem was found
	 * @return ProblemDescriptor with formatted description and fixes
	 */
	public static ProblemDescriptor build(
		ProblemHelper problemHelper,
		ScanIssue scanIssue,
		int problemLineNumber) {

		String description = formatDescription(scanIssue);
		List<Object> fixes = createFixes(scanIssue);

		return ProblemDescriptor.builder()
			.file(problemHelper.getFile())
			.scanIssue(scanIssue)
			.lineNumber(problemLineNumber)
			.description(description)
			.fixes(fixes)
			.build();
	}

	/**
	 * Format the problem description from scan issue details.
	 *
	 * @param scanIssue The scan issue
	 * @return HTML-formatted description for display
	 */
	private static String formatDescription(ScanIssue scanIssue) {
		StringBuilder sb = new StringBuilder();
		sb.append("<html>");
		sb.append("<b>").append(escapeHtml(scanIssue.getTitle())).append("</b>");
		sb.append("<br/>");
		sb.append("Severity: ").append(scanIssue.getSeverity());
		sb.append("<br/>");
		if (scanIssue.getDescription() != null && !scanIssue.getDescription().isEmpty()) {
			sb.append(escapeHtml(scanIssue.getDescription()));
		}
		sb.append("</html>");
		return sb.toString();
	}

	/**
	 * Escape HTML special characters for safe display.
	 *
	 * @param text Text to escape
	 * @return HTML-escaped text
	 */
	private static String escapeHtml(String text) {
		if (text == null) {
			return "";
		}
		return text
			.replace("&", "&amp;")
			.replace("<", "&lt;")
			.replace(">", "&gt;")
			.replace("\"", "&quot;")
			.replace("'", "&#39;");
	}

	/**
	 * Create fixes for a scan issue.
	 *
	 * Currently creates: ViewDetailsFix
	 * Can be extended with: IgnoreVulnerabilityFix, etc.
	 *
	 * @param scanIssue The scan issue
	 * @return List of fixes (currently all as Object, can be typed later)
	 */
	private static List<Object> createFixes(ScanIssue scanIssue) {
		List<Object> fixes = new ArrayList<>();
		// Future: add ViewDetailsFix, IgnoreVulnerabilityFix, etc.
		return fixes;
	}
}
