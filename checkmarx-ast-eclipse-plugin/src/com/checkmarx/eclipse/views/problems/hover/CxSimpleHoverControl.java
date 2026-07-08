package com.checkmarx.eclipse.views.problems.hover;

import org.eclipse.jface.text.AbstractInformationControl;
import org.eclipse.jface.text.IInformationControlExtension2;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.core.resources.IMarker;

import com.checkmarx.eclipse.views.problems.marker.ProblemMarkerConstants;
import com.checkmarx.eclipse.views.problems.ignored.IgnoredProblemsStore;
import com.checkmarx.eclipse.views.problems.CxProblemsServices;

/**
 * Robust custom hover control using native SWT components.
 *
 * Uses SWT Composite instead of Browser widget for maximum compatibility.
 * Displays vulnerability details with native SWT buttons (not HTML).
 *
 * Features:
 * - Simple, native SWT implementation
 * - Three action buttons: Ignore, Quick Fix, Ask AI
 * - Color-coded severity badge
 * - Works on all platforms without Browser widget limitations
 * - No JavaScript bridge needed
 */
public class CxSimpleHoverControl extends AbstractInformationControl implements IInformationControlExtension2 {

	private IMarker marker;
	private Composite mainComposite;
	private String findingId;

	public CxSimpleHoverControl(Shell parent, IMarker marker) {
		super(parent, true);
		this.marker = marker;
		System.out.println("[CX-HOVER-SIMPLE] CxSimpleHoverControl created");
		create();
	}

	@Override
	protected void createContent(Composite parent) {
		System.out.println("[CX-HOVER-SIMPLE] Creating content...");

		mainComposite = new Composite(parent, SWT.NONE);
		GridLayout layout = new GridLayout(1, false);
		layout.marginHeight = 8;
		layout.marginWidth = 8;
		layout.verticalSpacing = 6;
		mainComposite.setLayout(layout);
		mainComposite.setBackground(Display.getCurrent().getSystemColor(SWT.COLOR_WHITE));

		try {
			// Extract marker attributes
			String severity = (String) marker.getAttribute(ProblemMarkerConstants.ATTR_SEVERITY);
			String message = (String) marker.getAttribute(IMarker.MESSAGE);
			String ruleId = (String) marker.getAttribute(ProblemMarkerConstants.ATTR_RULE_ID);
			String status = (String) marker.getAttribute(ProblemMarkerConstants.ATTR_STATUS);
			findingId = (String) marker.getAttribute(ProblemMarkerConstants.ATTR_FINDING_ID);
			Integer lineNumber = (Integer) marker.getAttribute(IMarker.LINE_NUMBER);

			// 1. Severity Badge
			Composite severityComposite = new Composite(mainComposite, SWT.NONE);
			severityComposite.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
			GridLayout severityLayout = new GridLayout(2, false);
			severityLayout.marginHeight = 4;
			severityLayout.marginWidth = 6;
			severityLayout.verticalSpacing = 0;
			severityLayout.horizontalSpacing = 6;
			severityComposite.setLayout(severityLayout);
			severityComposite.setBackground(getSeverityColor(severity));

			Label severityIcon = new Label(severityComposite, SWT.NONE);
			severityIcon.setText(getSeverityIcon(severity));
			severityIcon.setForeground(Display.getCurrent().getSystemColor(SWT.COLOR_WHITE));
			severityIcon.setFont(mainComposite.getFont());

			Label severityLabel = new Label(severityComposite, SWT.NONE);
			severityLabel.setText(getSeverityLabel(severity));
			severityLabel.setForeground(Display.getCurrent().getSystemColor(SWT.COLOR_WHITE));
			severityLabel.setFont(mainComposite.getFont());

			// 2. Message
			Label messageLabel = new Label(mainComposite, SWT.WRAP);
			messageLabel.setText(message != null ? message : "Unknown issue");
			messageLabel.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));
			messageLabel.setForeground(Display.getCurrent().getSystemColor(SWT.COLOR_BLACK));

			// 3. Information rows
			createInfoRow(mainComposite, "Location:", "Line " + (lineNumber != null ? lineNumber : "?"));
			createInfoRow(mainComposite, "Rule ID:", ruleId != null && !ruleId.isEmpty() ? ruleId : "N/A");
			createInfoRow(mainComposite, "Finding ID:", findingId != null && !findingId.isEmpty() ? findingId.substring(0, Math.min(16, findingId.length())) : "N/A");
			createInfoRow(mainComposite, "Status:", status != null ? status : "UNKNOWN");

			// 4. Action Buttons
			Composite buttonComposite = new Composite(mainComposite, SWT.NONE);
			buttonComposite.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
			GridLayout buttonLayout = new GridLayout(3, true);
			buttonLayout.marginHeight = 4;
			buttonLayout.marginWidth = 0;
			buttonLayout.horizontalSpacing = 6;
			buttonComposite.setLayout(buttonLayout);

			// Ignore button
			Button ignoreBtn = new Button(buttonComposite, SWT.PUSH);
			ignoreBtn.setText("🚫 Ignore");
			ignoreBtn.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
			ignoreBtn.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					System.out.println("[CX-HOVER-SIMPLE] Ignore button clicked");
					handleIgnoreAction();
				}
			});

			// Quick Fix button
			Button fixBtn = new Button(buttonComposite, SWT.PUSH);
			fixBtn.setText("✓ Quick Fix");
			fixBtn.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
			fixBtn.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					System.out.println("[CX-HOVER-SIMPLE] Quick Fix button clicked");
					handleQuickFixAction();
				}
			});

			// Ask AI button
			Button chatBtn = new Button(buttonComposite, SWT.PUSH);
			chatBtn.setText("💬 Ask AI");
			chatBtn.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
			chatBtn.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					System.out.println("[CX-HOVER-SIMPLE] Ask AI button clicked");
					handleChatAction();
				}
			});

			System.out.println("[CX-HOVER-SIMPLE] ✓ Content created successfully");

		} catch (Exception e) {
			System.err.println("[CX-HOVER-SIMPLE] Error creating content: " + e.getMessage());
			e.printStackTrace();
		}
	}

	/**
	 * Create a row with label and value in the hover
	 */
	private void createInfoRow(Composite parent, String label, String value) {
		Composite row = new Composite(parent, SWT.NONE);
		row.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		GridLayout rowLayout = new GridLayout(2, false);
		rowLayout.marginHeight = 0;
		rowLayout.marginWidth = 0;
		rowLayout.horizontalSpacing = 8;
		row.setLayout(rowLayout);

		Label labelWidget = new Label(row, SWT.NONE);
		labelWidget.setText(label);
		labelWidget.setForeground(Display.getCurrent().getSystemColor(SWT.COLOR_DARK_GRAY));

		Label valueWidget = new Label(row, SWT.WRAP);
		valueWidget.setText(value);
		valueWidget.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		valueWidget.setForeground(Display.getCurrent().getSystemColor(SWT.COLOR_BLACK));
	}

	/**
	 * Get background color based on severity
	 */
	private Color getSeverityColor(String severity) {
		if (severity == null) return Display.getCurrent().getSystemColor(SWT.COLOR_GRAY);
		switch (severity.toUpperCase()) {
			case "CRITICAL":
				return Display.getCurrent().getSystemColor(SWT.COLOR_RED);
			case "HIGH":
				return Display.getCurrent().getSystemColor(SWT.COLOR_DARK_YELLOW);
			case "MEDIUM":
				return Display.getCurrent().getSystemColor(SWT.COLOR_YELLOW);
			case "LOW":
				return Display.getCurrent().getSystemColor(SWT.COLOR_GREEN);
			default:
				return Display.getCurrent().getSystemColor(SWT.COLOR_GRAY);
		}
	}

	/**
	 * Get severity emoji icon
	 */
	private String getSeverityIcon(String severity) {
		if (severity == null) return "⚠️";
		switch (severity.toUpperCase()) {
			case "CRITICAL":
				return "🔴";
			case "HIGH":
				return "🟠";
			case "MEDIUM":
				return "🟡";
			case "LOW":
				return "🟢";
			default:
				return "⚠️";
		}
	}

	/**
	 * Get severity label text
	 */
	private String getSeverityLabel(String severity) {
		if (severity == null) return "UNKNOWN";
		return severity.toUpperCase();
	}

	/**
	 * Handle ignore action
	 */
	private void handleIgnoreAction() {
		try {
			if (findingId != null) {
				IgnoredProblemsStore ignoredStore = IgnoredProblemsStore.getInstance();
				ignoredStore.ignoreProblem(findingId);
				CxProblemsServices.publisher().publish();
				System.out.println("[CX-HOVER-SIMPLE] ✓ Problem ignored: " + findingId);
			}
			dispose();
		} catch (Exception e) {
			System.err.println("[CX-HOVER-SIMPLE] Error ignoring problem: " + e.getMessage());
		}
	}

	/**
	 * Handle quick fix action
	 */
	private void handleQuickFixAction() {
		try {
			org.eclipse.ui.commands.ICommandService commandService = (org.eclipse.ui.commands.ICommandService) org.eclipse.ui.PlatformUI
					.getWorkbench().getService(org.eclipse.ui.commands.ICommandService.class);
			if (commandService != null) {
				commandService.getCommand("org.eclipse.jdt.ui.edit.text.java.quick.assist")
						.executeWithChecks(new org.eclipse.core.commands.ExecutionEvent());
				System.out.println("[CX-HOVER-SIMPLE] ✓ Quick fix triggered");
			}
			dispose();
		} catch (Exception e) {
			System.err.println("[CX-HOVER-SIMPLE] Error triggering quick fix: " + e.getMessage());
		}
	}

	/**
	 * Handle ask AI action
	 */
	private void handleChatAction() {
		try {
			String message = (String) marker.getAttribute(IMarker.MESSAGE);
			String severity = (String) marker.getAttribute(ProblemMarkerConstants.ATTR_SEVERITY);
			Integer lineNumber = (Integer) marker.getAttribute(IMarker.LINE_NUMBER);

			String prompt = String.format(
					"Please help me fix this security issue:\n\n" +
					"**Issue:** %s\n" +
					"**Severity:** %s\n" +
					"**Line:** %s\n" +
					"**Finding ID:** %s\n\n" +
					"Provide a secure fix with code example.",
					message != null ? message : "Unknown",
					severity != null ? severity : "Unknown",
					lineNumber != null ? lineNumber : "?",
					findingId);

			// Copy to clipboard
			org.eclipse.swt.dnd.Clipboard clipboard = new org.eclipse.swt.dnd.Clipboard(Display.getCurrent());
			org.eclipse.swt.dnd.TextTransfer transfer = org.eclipse.swt.dnd.TextTransfer.getInstance();
			clipboard.setContents(new Object[] { prompt }, new org.eclipse.swt.dnd.Transfer[] { transfer });
			clipboard.dispose();

			System.out.println("[CX-HOVER-SIMPLE] ✓ Prompt copied to clipboard");

			org.eclipse.jface.dialogs.MessageDialog.openInformation(getShell(), "Checkmarx",
					"Fix prompt copied to clipboard!\n\nPaste in Copilot Chat or your preferred AI chat.");

			dispose();
		} catch (Exception e) {
			System.err.println("[CX-HOVER-SIMPLE] Error in chat action: " + e.getMessage());
		}
	}

	@Override
	public void setInput(Object input) {
		// Not needed
	}

	@Override
	public void setSize(int width, int height) {
		try {
			Shell shell = getShell();
			if (shell != null && !shell.isDisposed()) {
				shell.setSize(width, height);
			}
		} catch (Exception e) {
			// Ignore
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
			// Ignore
		}
	}

	@Override
	public void setSizeConstraints(int maxWidth, int maxHeight) {
		// Not needed
	}

	@Override
	public Point computeSizeHint() {
		try {
			Shell shell = getShell();
			if (shell != null && !shell.isDisposed()) {
				return shell.computeSize(400, SWT.DEFAULT);
			}
		} catch (Exception e) {
			// Ignore
		}
		return new Point(400, 300);
	}

	@Override
	public void addDisposeListener(org.eclipse.swt.events.DisposeListener listener) {
		try {
			Shell shell = getShell();
			if (shell != null && !shell.isDisposed()) {
				shell.addDisposeListener(listener);
			}
		} catch (Exception e) {
			// Ignore
		}
	}

	@Override
	public void removeDisposeListener(org.eclipse.swt.events.DisposeListener listener) {
		try {
			Shell shell = getShell();
			if (shell != null && !shell.isDisposed()) {
				shell.removeDisposeListener(listener);
			}
		} catch (Exception e) {
			// Ignore
		}
	}

	@Override
	public void setBackgroundColor(Color background) {
		try {
			super.setBackgroundColor(background);
			if (mainComposite != null && !mainComposite.isDisposed()) {
				mainComposite.setBackground(background);
			}
		} catch (Exception e) {
			// Ignore
		}
	}

	@Override
	public void setForegroundColor(Color foreground) {
		try {
			super.setForegroundColor(foreground);
		} catch (Exception e) {
			// Ignore
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
			// Ignore
		}
	}

	@Override
	public void dispose() {
		try {
			super.dispose();
		} catch (Exception e) {
			// Ignore
		}
	}
}
