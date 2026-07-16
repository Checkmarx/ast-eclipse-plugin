package com.checkmarx.eclipse.devassist.problems.hover;

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
import java.util.Timer;
import java.util.TimerTask;

/**
 * Custom hover control for Checkmarx findings.
 *
 * Displays:
 * - Issue severity badge
 * - Issue message/title
 * - 4 Clickable user name buttons with different actions
 *
 * Includes 1.5 second delay before closing to allow button interactions.
 */
public class CxSimpleHoverControl extends AbstractInformationControl implements IInformationControlExtension2 {

	private IMarker marker;
	private Composite mainComposite;
	private String findingId;
	private String userName;
	private Timer closeTimer;

	public CxSimpleHoverControl(Shell parent, IMarker marker) {
		super(parent, true);
		this.marker = marker;
		this.userName = getUserName();
		System.out.println("[HOVER] CxSimpleHoverControl created for user: " + userName);
		create();
	}

	@Override
	protected void createContent(Composite parent) {
		System.out.println("[HOVER] Creating hover content...");

		mainComposite = new Composite(parent, SWT.NONE);
		GridLayout layout = new GridLayout(1, false);
		layout.marginHeight = 10;
		layout.marginWidth = 10;
		layout.verticalSpacing = 8;
		mainComposite.setLayout(layout);
		mainComposite.setBackground(Display.getCurrent().getSystemColor(SWT.COLOR_WHITE));

		try {
			// Extract marker attributes
			Object severityObj = marker.getAttribute("severity");
			String severity = severityObj != null ? severityObj.toString() : null;
			String message = (String) marker.getAttribute(IMarker.MESSAGE);
			Integer lineNumber = (Integer) marker.getAttribute(IMarker.LINE_NUMBER);
			findingId = (String) marker.getAttribute("findingId");

			System.out.println("[HOVER] Severity: " + severity + ", Message: " + message);

			// 1. Severity Badge
			createSeverityBadge(severity);

			// 2. Issue Message
			createMessageSection(message, lineNumber);

			// 3. Separator
			Label separator = new Label(mainComposite, SWT.SEPARATOR | SWT.HORIZONTAL);
			separator.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

			// 4. User Name Buttons (4 clickable buttons)
			createUserNameButtonsSection();
			parent.layout();

			// Add mouse listeners to BOTH parent and mainComposite AFTER all content is created
			addMouseTrackingToAllChildren(mainComposite);
			addMouseTrackingToAllChildren(parent);

			System.out.println("[HOVER] ✓ Content created successfully");

		} catch (Exception e) {
			System.err.println("[HOVER] Error creating content: " + e.getMessage());
			e.printStackTrace();
		}
	}

	private void addMouseTrackingToAllChildren(Composite composite) {
		if (composite == null || composite.isDisposed()) return;

		composite.addMouseTrackListener(new org.eclipse.swt.events.MouseTrackListener() {
			@Override
			public void mouseEnter(org.eclipse.swt.events.MouseEvent e) {
				System.out.println("[HOVER] Mouse ENTERED control");
				if (closeTimer != null) {
					closeTimer.cancel();
					closeTimer = null;
				}
			}

			@Override
			public void mouseExit(org.eclipse.swt.events.MouseEvent e) {
				System.out.println("[HOVER] Mouse EXITED control - starting close timer");
				if (closeTimer != null) {
					closeTimer.cancel();
				}
				closeTimer = new Timer();
				closeTimer.schedule(new TimerTask() {
					@Override
					public void run() {
						Display.getDefault().asyncExec(() -> {
							try {
								CxSimpleHoverControl.super.dispose();
							} catch (Exception ex) {
								// Already disposed
							}
						});
					}
				}, 500);
			}

			@Override
			public void mouseHover(org.eclipse.swt.events.MouseEvent e) {
				// Not needed
			}
		});

		// Recursively add to all children
		for (org.eclipse.swt.widgets.Control child : composite.getChildren()) {
			if (child instanceof Composite) {
				addMouseTrackingToAllChildren((Composite) child);
			}
		}
	}

	/**
	 * Create severity badge
	 */
	private void createSeverityBadge(String severity) {
		Composite severityComposite = new Composite(mainComposite, SWT.NONE);
		severityComposite.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		GridLayout severityLayout = new GridLayout(2, false);
		severityLayout.marginHeight = 6;
		severityLayout.marginWidth = 8;
		severityLayout.verticalSpacing = 0;
		severityLayout.horizontalSpacing = 8;
		severityComposite.setLayout(severityLayout);
		severityComposite.setBackground(getSeverityColor(severity));

		Label severityIcon = new Label(severityComposite, SWT.NONE);
		severityIcon.setText(getSeverityIcon(severity));
		severityIcon.setForeground(Display.getCurrent().getSystemColor(SWT.COLOR_WHITE));
		severityIcon.setFont(mainComposite.getFont());

		Label severityLabel = new Label(severityComposite, SWT.NONE);
		String severityText = severity != null ? severity.toUpperCase() : "UNKNOWN";
		severityLabel.setText(severityText + " SEVERITY");
		severityLabel.setForeground(Display.getCurrent().getSystemColor(SWT.COLOR_WHITE));
		severityLabel.setFont(mainComposite.getFont());
	}

	/**
	 * Create message/title section
	 */
	private void createMessageSection(String message, Integer lineNumber) {
		Label messageLabel = new Label(mainComposite, SWT.WRAP);
		messageLabel.setText(message != null ? message : "Security Issue Detected");
		messageLabel.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		messageLabel.setForeground(Display.getCurrent().getSystemColor(SWT.COLOR_BLACK));

		// Line number info
		if (lineNumber != null) {
			Label lineLabel = new Label(mainComposite, SWT.NONE);
			lineLabel.setText("Line: " + lineNumber);
			lineLabel.setForeground(Display.getCurrent().getSystemColor(SWT.COLOR_DARK_GRAY));
		}
	}

	/**
	 * Create 4 clickable user name buttons
	 */
	private void createUserNameButtonsSection() {
		Label titleLabel = new Label(mainComposite, SWT.NONE);
		titleLabel.setText("[USER] Actions by " + userName + ":");
		titleLabel.setForeground(Display.getCurrent().getSystemColor(SWT.COLOR_DARK_GRAY));
		titleLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

		// 4 buttons in a row
		Composite buttonComposite = new Composite(mainComposite, SWT.NONE);
		buttonComposite.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		GridLayout buttonLayout = new GridLayout(4, true);
		buttonLayout.marginHeight = 4;
		buttonLayout.marginWidth = 0;
		buttonLayout.horizontalSpacing = 4;
		buttonComposite.setLayout(buttonLayout);

		// Button 1: Assign
		createNameButton(buttonComposite, 1, "[A] Assign", userName, "Assign to " + userName);

		// Button 2: Comment
		createNameButton(buttonComposite, 2, "[C] Comment", userName, "Add comment by " + userName);

		// Button 3: Approve
		createNameButton(buttonComposite, 3, "[OK] Approve", userName, "Approve by " + userName);

		// Button 4: Flag
		createNameButton(buttonComposite, 4, "[!] Flag", userName, "Flag by " + userName);
	}

	/**
	 * Create a single user name button
	 */
	private void createNameButton(Composite parent, int buttonIndex, String label, String name, String tooltip) {
		Button btn = new Button(parent, SWT.PUSH);
		btn.setText(label);
		btn.setToolTipText(tooltip);
		btn.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		System.out.println("[HOVER] Created button #" + buttonIndex + ": " + label);

		btn.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				System.out.println("[HOVER] ✓✓✓ Button #" + buttonIndex + " CLICKED: " + name);
				handleUserNameButtonClick(buttonIndex, name);
			}
		});
	}

	/**
	 * Handle user name button click (1-4)
	 */
	private void handleUserNameButtonClick(int buttonIndex, String userName) {
		System.out.println("[HOVER] ========================================");
		System.out.println("[HOVER] Button #" + buttonIndex + " Action");
		System.out.println("[HOVER] User: " + userName);
		System.out.println("[HOVER] Finding ID: " + findingId);
		System.out.println("[HOVER] ========================================");

		String actionMessage = "";
		switch (buttonIndex) {
			case 1:
				actionMessage = "✓ Issue assigned to: " + userName;
				break;
			case 2:
				actionMessage = "✓ Comment added by: " + userName + "\n" + new java.util.Date();
				break;
			case 3:
				actionMessage = "✓ Issue approved by: " + userName;
				break;
			case 4:
				actionMessage = "✓ Issue flagged by: " + userName + " for review";
				break;
		}

		showActionNotification("Checkmarx Action", actionMessage);
	}

	/**
	 * Show action notification dialog
	 */
	private void showActionNotification(String title, String message) {
		try {
			Display.getDefault().asyncExec(() -> {
				org.eclipse.jface.dialogs.MessageDialog.openInformation(
					getShell(),
					title,
					message + "\n\nFinding ID: " + findingId);
			});
		} catch (Exception e) {
			System.err.println("[HOVER] Error showing notification: " + e.getMessage());
		}
	}

	/**
	 * Get user name from git config or system
	 */
	private String getUserName() {
		try {
			// Try git config first
			Process process = Runtime.getRuntime().exec("git config user.name");
			java.io.BufferedReader reader = new java.io.BufferedReader(
					new java.io.InputStreamReader(process.getInputStream()));
			String gitUserName = reader.readLine();
			reader.close();

			if (gitUserName != null && !gitUserName.trim().isEmpty()) {
				System.out.println("[HOVER] Git user: " + gitUserName);
				return gitUserName.trim();
			}
		} catch (Exception e) {
			System.out.println("[HOVER] Could not read git config: " + e.getMessage());
		}

		// Fallback to system user
		String systemUser = System.getProperty("user.name", "Developer");
		System.out.println("[HOVER] System user: " + systemUser);
		return systemUser;
	}

	/**
	 * Get severity color
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
	 * Get severity icon emoji
	 */
	private String getSeverityIcon(String severity) {
		if (severity == null) return "[!]";
		switch (severity.toUpperCase()) {
			case "CRITICAL":
				return "[C]";
			case "HIGH":
				return "[H]";
			case "MEDIUM":
				return "[M]";
			case "LOW":
				return "[L]";
			default:
				return "[!]";
		}
	}

	// ============ IInformationControl Implementation ============

	@Override
	public void setInput(Object input) {
		System.out.println("[HOVER] setInput called with: " + input);
		if (input instanceof IMarker) {
			this.marker = (IMarker) input;
			System.out.println("[HOVER] Marker updated from setInput");
		}
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
			if (shell != null && !shell.isDisposed() && location != null) {
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
		return new Point(400, 250);
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

	public boolean isFocusable() {
		return true;
	}

	public void setFocus() {
		if (mainComposite != null && !mainComposite.isDisposed()) {
			mainComposite.setFocus();
		}
	}

	@Override
	public void dispose() {
		// IMPORTANT: Framework calls dispose() when mouse leaves hover region
		// Set a long timer to give user time to move mouse from code to buttons
		// Timer will be canceled by mouseEnter if user successfully reaches the buttons
		if (closeTimer != null) {
			closeTimer.cancel();
		}
		closeTimer = new Timer();
		closeTimer.schedule(new TimerTask() {
			@Override
			public void run() {
				Display.getDefault().asyncExec(() -> {
					try {
						CxSimpleHoverControl.super.dispose();
					} catch (Exception e) {
						// Already disposed
					}
				});
			}
		}, 3000);  // 3 second delay - gives user time to move mouse to buttons
	}
}
