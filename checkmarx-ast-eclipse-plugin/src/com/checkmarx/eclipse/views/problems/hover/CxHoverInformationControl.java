package com.checkmarx.eclipse.views.problems.hover;

import org.eclipse.jface.text.AbstractInformationControl;
import org.eclipse.jface.text.IInformationControlExtension2;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.browser.BrowserFunction;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.core.resources.IMarker;

import com.checkmarx.eclipse.views.problems.marker.ProblemMarkerConstants;
import com.checkmarx.eclipse.views.problems.ignored.IgnoredProblemsStore;
import com.checkmarx.eclipse.views.problems.CxProblemsServices;

/**
 * Custom information control for Checkmarx problem hover popup.
 *
 * Displays comprehensive vulnerability details in an HTML popup:
 * - Severity level with color coding
 * - Complete problem description
 * - Line and location information
 * - Rule ID and Finding ID for reference
 * - Interactive action buttons:
 *   * Ignore This Problem - marks as ignored, removes from view
 *   * Quick Fix - triggers Eclipse quick assist/fix recommendations
 *   * Ask AI - copies vulnerability details to clipboard for pasting into AI chat
 *
 * Uses a Browser widget to render HTML, allowing for rich formatting and styling.
 */
public class CxHoverInformationControl extends AbstractInformationControl implements IInformationControlExtension2 {

	private IMarker marker;
	private Composite mainComposite;
	private Browser browser;
	private String findingId;

	public CxHoverInformationControl(Shell parent, IMarker marker, ITextViewer textViewer) {
		super(parent, true);
		this.marker = marker;
		create();
	}

	@Override
	protected void createContent(Composite parent) {
		mainComposite = new Composite(parent, SWT.NONE);
		GridLayout layout = new GridLayout(1, false);
		layout.marginHeight = 0;
		layout.marginWidth = 0;
		layout.verticalSpacing = 0;
		mainComposite.setLayout(layout);

		try {
			// Extract marker attributes containing vulnerability details
			String severity = (String) marker.getAttribute(ProblemMarkerConstants.ATTR_SEVERITY);
			String message = (String) marker.getAttribute(IMarker.MESSAGE);
			String ruleId = (String) marker.getAttribute(ProblemMarkerConstants.ATTR_RULE_ID);
			String status = (String) marker.getAttribute(ProblemMarkerConstants.ATTR_STATUS);
			findingId = (String) marker.getAttribute(ProblemMarkerConstants.ATTR_FINDING_ID);
			Integer lineNumber = (Integer) marker.getAttribute(IMarker.LINE_NUMBER);
			Integer charStart = (Integer) marker.getAttribute(IMarker.CHAR_START);
			Integer charEnd = (Integer) marker.getAttribute(IMarker.CHAR_END);

			System.out.println("[CX-HOVER] Creating hover content for finding: " + findingId);

			// Create Browser widget for rendering rich HTML content
			browser = new Browser(mainComposite, SWT.NONE);
			browser.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
			System.out.println("[CX-HOVER] Browser widget created");

			// Register Java-JavaScript bridge functions BEFORE setting HTML
			// This ensures functions are available when page loads
			registerBrowserFunctions();
			System.out.println("[CX-HOVER] JavaScript bridge functions registered");

			// Generate and display rich HTML content
			String html = generateHtmlContent(severity, message, ruleId, status, lineNumber, charStart, charEnd, findingId);
			browser.setText(html);
			System.out.println("[CX-HOVER] HTML content set in browser widget");

		} catch (Exception e) {
			System.err.println("[CX-HOVER] Error creating hover content: " + e.getMessage());
			e.printStackTrace();
		}
	}

	/**
	 * Generate rich HTML content for the hover popup.
	 * Displays vulnerability details with severity color-coding and action buttons.
	 */
	private String generateHtmlContent(String severity, String message, String ruleId, String status,
			Integer lineNumber, Integer charStart, Integer charEnd, String findingId) {
		String severityColor = getSeverityHtmlColor(severity);
		String severityLabel = getSeverityLabel(severity);

		StringBuilder locationInfo = new StringBuilder();
		if (lineNumber != null) {
			locationInfo.append("Line: ").append(lineNumber);
			if (charStart != null && charEnd != null) {
				locationInfo.append(" (").append(charStart).append("-").append(charEnd).append(")");
			}
		} else {
			locationInfo.append("Line: ?");
		}

		String statusBadge = status != null ? status : "UNKNOWN";
		String ruleIdText = ruleId != null && !ruleId.isEmpty() ? ruleId : "N/A";
		String findingIdText = findingId != null && !findingId.isEmpty() ? findingId : "N/A";

		// Simplified HTML for better Eclipse browser widget compatibility
		StringBuilder html = new StringBuilder();
		html.append("<!DOCTYPE html>\n");
		html.append("<html>\n");
		html.append("<head>\n");
		html.append("<style type='text/css'>\n");
		html.append("body { font-family: Arial, sans-serif; margin: 4px; padding: 4px; background: white; }\n");
		html.append(".severity { display: inline-block; padding: 3px 6px; border-radius: 2px; background: ").append(severityColor).append("; color: white; font-weight: bold; font-size: 11px; margin-bottom: 6px; }\n");
		html.append(".message { font-size: 12px; margin: 6px 0px; line-height: 1.4; }\n");
		html.append(".info { font-size: 10px; color: #555; margin: 3px 0px; }\n");
		html.append(".buttons { margin-top: 8px; }\n");
		html.append("button { margin-right: 4px; margin-bottom: 4px; padding: 4px 8px; border: none; border-radius: 2px; cursor: pointer; font-size: 10px; font-weight: bold; color: white; }\n");
		html.append(".btn-ignore { background-color: #dc3545; }\n");
		html.append(".btn-ignore:hover { background-color: #c82333; }\n");
		html.append(".btn-fix { background-color: #28a745; }\n");
		html.append(".btn-fix:hover { background-color: #218838; }\n");
		html.append(".btn-chat { background-color: #007bff; }\n");
		html.append(".btn-chat:hover { background-color: #0056b3; }\n");
		html.append("</style>\n");
		html.append("</head>\n");
		html.append("<body>\n");

		// Severity badge
		html.append("<div class='severity'>").append(severityLabel).append("</div>\n");

		// Message
		html.append("<div class='message'>").append(escapeHtml(message != null ? message : "Unknown issue")).append("</div>\n");

		// Information rows
		html.append("<div class='info'><b>Location:</b> ").append(locationInfo.toString()).append("</div>\n");
		html.append("<div class='info'><b>Rule:</b> ").append(escapeHtml(ruleIdText)).append("</div>\n");
		html.append("<div class='info'><b>Finding ID:</b> ").append(escapeHtml(findingIdText.substring(0, Math.min(16, findingIdText.length())))).append("</div>\n");
		html.append("<div class='info'><b>Status:</b> ").append(escapeHtml(statusBadge)).append("</div>\n");

		// Action buttons
		html.append("<div class='buttons'>\n");
		html.append("  <button class='btn-ignore' onclick='ignoreAction()'>Ignore</button>\n");
		html.append("  <button class='btn-fix' onclick='quickFixAction()'>Quick Fix</button>\n");
		html.append("  <button class='btn-chat' onclick='chatAction()'>Ask AI</button>\n");
		html.append("</div>\n");

		html.append("</body>\n");
		html.append("</html>\n");

		String result = html.toString();
		System.out.println("[CX-HOVER] Generated HTML length: " + result.length() + " chars");
		return result;
	}

	private String getSeverityHtmlColor(String severity) {
		if (severity == null) return "#999999";
		switch (severity.toUpperCase()) {
			case "CRITICAL": return "#dc3545"; // Red
			case "HIGH": return "#fd7e14"; // Orange
			case "MEDIUM": return "#ffc107"; // Yellow
			case "LOW": return "#28a745"; // Green
			default: return "#6c757d"; // Gray
		}
	}

	private String getSeverityLabel(String severity) {
		if (severity == null) return "⚠️ UNKNOWN";
		switch (severity.toUpperCase()) {
			case "CRITICAL": return "🔴 CRITICAL";
			case "HIGH": return "🟠 HIGH";
			case "MEDIUM": return "🟡 MEDIUM";
			case "LOW": return "🟢 LOW";
			default: return "⚠️ " + severity.toUpperCase();
		}
	}

	private String escapeHtml(String text) {
		if (text == null) return "";
		return text
			.replace("&", "&amp;")
			.replace("<", "&lt;")
			.replace(">", "&gt;")
			.replace("\"", "&quot;")
			.replace("'", "&#39;");
	}


	/**
	 * Register Java-JavaScript bridge functions for interactive hover actions.
	 * These functions must be registered BEFORE the HTML is loaded into the browser.
	 */
	private void registerBrowserFunctions() {
		try {
			new BrowserFunction(browser, "ignoreAction") {
				@Override
				public Object function(Object[] args) {
					System.out.println("[CX-HOVER] ignoreAction() called from JavaScript");
					Display.getCurrent().asyncExec(() -> handleIgnoreAction(findingId));
					return null;
				}
			};
			System.out.println("[CX-HOVER] Registered ignoreAction function");

			new BrowserFunction(browser, "quickFixAction") {
				@Override
				public Object function(Object[] args) {
					System.out.println("[CX-HOVER] quickFixAction() called from JavaScript");
					Display.getCurrent().asyncExec(() -> handleQuickFixAction());
					return null;
				}
			};
			System.out.println("[CX-HOVER] Registered quickFixAction function");

			new BrowserFunction(browser, "chatAction") {
				@Override
				public Object function(Object[] args) {
					System.out.println("[CX-HOVER] chatAction() called from JavaScript");
					Display.getCurrent().asyncExec(() -> handlePasteToChatAction(findingId));
					return null;
				}
			};
			System.out.println("[CX-HOVER] Registered chatAction function");
		} catch (Exception e) {
			System.err.println("[CX-HOVER] Error registering browser functions: " + e.getMessage());
			e.printStackTrace();
		}
	}

	/**
	 * Mark the problem as ignored and republish the Problems View without it.
	 */
	private void handleIgnoreAction(String findingId) {
		try {
			IgnoredProblemsStore ignoredStore = IgnoredProblemsStore.getInstance();
			ignoredStore.ignoreProblem(findingId);
			CxProblemsServices.publisher().publish();
			dispose();
		} catch (Exception e) {
			// Silently ignore errors
		}
	}

	/**
	 * Trigger Eclipse Quick Assist (Ctrl+1) command for fix suggestions.
	 */
	private void handleQuickFixAction() {
		try {
			org.eclipse.ui.commands.ICommandService commandService =
				(org.eclipse.ui.commands.ICommandService) org.eclipse.ui.PlatformUI.getWorkbench()
					.getService(org.eclipse.ui.commands.ICommandService.class);
			if (commandService != null) {
				commandService.getCommand("org.eclipse.jdt.ui.edit.text.java.quick.assist").executeWithChecks(
					new org.eclipse.core.commands.ExecutionEvent());
			}
			dispose();
		} catch (Exception e) {
			// Silently ignore errors
		}
	}

	/**
	 * Build AI-friendly remediation prompt and copy to clipboard for pasting into AI chat.
	 */
	private void handlePasteToChatAction(String findingId) {
		try {
			String prompt = buildRemediationPrompt(findingId);

			// Copy prompt to clipboard
			org.eclipse.swt.dnd.Clipboard clipboard = new org.eclipse.swt.dnd.Clipboard(Display.getCurrent());
			org.eclipse.swt.dnd.TextTransfer transfer = org.eclipse.swt.dnd.TextTransfer.getInstance();
			clipboard.setContents(new Object[] { prompt }, new org.eclipse.swt.dnd.Transfer[] { transfer });
			clipboard.dispose();

			// Show notification
			org.eclipse.jface.dialogs.MessageDialog.openInformation(
				getShell(),
				"Checkmarx",
				"Fix prompt copied to clipboard!\n\nPaste in Copilot Chat (Ctrl+Alt+I) or your preferred AI chat.");

			dispose();
		} catch (Exception e) {
			// Silently ignore errors
		}
	}

	private String buildRemediationPrompt(String findingId) {
		try {
			String message = (String) marker.getAttribute(IMarker.MESSAGE);
			String severity = (String) marker.getAttribute(ProblemMarkerConstants.ATTR_SEVERITY);
			Integer lineNumber = (Integer) marker.getAttribute(IMarker.LINE_NUMBER);

			return String.format(
				"Please help me fix this security issue:\n\n" +
				"**Issue:** %s\n" +
				"**Severity:** %s\n" +
				"**Line:** %s\n" +
				"**Finding ID:** %s\n\n" +
				"Provide a secure fix with code example.",
				message != null ? message : "Unknown",
				severity != null ? severity : "Unknown",
				lineNumber != null ? lineNumber : "?",
				findingId
			);
		} catch (Exception e) {
			return "Please help me fix this Checkmarx security finding: " + findingId;
		}
	}


@Override
	public void setInput(Object input) {
		// Not needed for this implementation
	}

	@Override
	public void setSize(int width, int height) {
		try {
			Shell shell = getShell();
			if (shell != null && !shell.isDisposed()) {
				shell.setSize(width, height);
			}
		} catch (Exception e) {
			// Widget may have been disposed; silently ignore
		}
	}

	@Override
	public void setLocation(Point location) {
		try {
			Shell shell = getShell();
			if (shell != null && !shell.isDisposed()) {
				shell.setLocation(location);
			}
		} catch (Exception e) {
			// Widget may have been disposed; silently ignore
		}
	}

	@Override
	public void setSizeConstraints(int maxWidth, int maxHeight) {
		// Not needed
	}

	@Override
	public Point computeSizeHint() {
		return getShell().computeSize(SWT.DEFAULT, SWT.DEFAULT);
	}

	@Override
	public void addDisposeListener(org.eclipse.swt.events.DisposeListener listener) {
		getShell().addDisposeListener(listener);
	}

	@Override
	public void removeDisposeListener(org.eclipse.swt.events.DisposeListener listener) {
		getShell().removeDisposeListener(listener);
	}

	@Override
	public void setBackgroundColor(Color background) {
		try {
			super.setBackgroundColor(background);
			if (mainComposite != null && !mainComposite.isDisposed()) {
				mainComposite.setBackground(background);
			}
		} catch (Exception e) {
			// Widget may have been disposed; silently ignore
		}
	}

	@Override
	public void setForegroundColor(Color foreground) {
		try {
			super.setForegroundColor(foreground);
		} catch (Exception e) {
			// Widget may have been disposed; silently ignore
		}
	}

	@Override
	public boolean hasContents() {
		return true;
	}

	@Override
	public void setVisible(boolean visible) {
		try {
			Shell shell = getShell();
			if (shell != null && !shell.isDisposed()) {
				shell.setVisible(visible);
			}
		} catch (Exception e) {
			// Widget may have been disposed; silently ignore
		}
	}

	@Override
	public void dispose() {
		try {
			super.dispose();
		} catch (Exception e) {
			// Already disposed or in process; silently ignore
		}
	}
}
