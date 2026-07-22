package com.checkmarx.eclipse.devassist.ui.findings.editor;

import org.eclipse.jface.text.AbstractInformationControl;
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
import java.util.Timer;
import java.util.TimerTask;

/**
 * Custom hover control for Checkmarx Findings.
 *
 * Displays:
 * - Issue severity badge with color
 * - Issue title/message
 * - Line number
 * - Action buttons (Ignore, View Details, etc.)
 *
 * Includes auto-close timer that pauses on mouse hover.
 */
public class CxFindingsHoverControl extends AbstractInformationControl {

	private FindingsAnnotation annotation;
	private Composite mainComposite;
	private Timer closeTimer;

	public CxFindingsHoverControl(Shell parent, FindingsAnnotation annotation) {
		super(parent, true);
		this.annotation = annotation;
		System.out.println("[FINDINGS-HOVER] CxFindingsHoverControl created");
		create();
	}

	@Override
	public boolean hasContents() {
		return annotation != null && annotation.getTitle() != null;
	}

	@Override
	protected void createContent(Composite parent) {
		System.out.println("[FINDINGS-HOVER] Creating hover content...");

		mainComposite = new Composite(parent, SWT.NONE);
		GridLayout layout = new GridLayout(1, false);
		layout.marginHeight = 10;
		layout.marginWidth = 10;
		layout.verticalSpacing = 8;
		mainComposite.setLayout(layout);
		mainComposite.setBackground(Display.getCurrent().getSystemColor(SWT.COLOR_WHITE));

		try {
			// 1. Severity Badge
			createSeverityBadge();

			// 2. Title/Message
			createMessageSection();

			// 3. Separator
			Label separator = new Label(mainComposite, SWT.SEPARATOR | SWT.HORIZONTAL);
			separator.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

			// 4. Action Buttons
			createActionButtonsSection();

			parent.layout();

			// Add mouse tracking to keep popup open when hovering
			addMouseTrackingToAllChildren(mainComposite);
			addMouseTrackingToAllChildren(parent);

			System.out.println("[FINDINGS-HOVER] ✓ Content created successfully");

		} catch (Exception e) {
			System.err.println("[FINDINGS-HOVER] Error creating content: " + e.getMessage());
			e.printStackTrace();
		}
	}

	private void addMouseTrackingToAllChildren(Composite composite) {
		if (composite == null || composite.isDisposed()) return;

		composite.addMouseTrackListener(new org.eclipse.swt.events.MouseTrackListener() {
			@Override
			public void mouseEnter(org.eclipse.swt.events.MouseEvent e) {
				System.out.println("[FINDINGS-HOVER] Mouse ENTERED control");
				if (closeTimer != null) {
					closeTimer.cancel();
					closeTimer = null;
				}
			}

			@Override
			public void mouseExit(org.eclipse.swt.events.MouseEvent e) {
				System.out.println("[FINDINGS-HOVER] Mouse EXITED control - starting close timer");
				if (closeTimer != null) {
					closeTimer.cancel();
				}
				closeTimer = new Timer();
				closeTimer.schedule(new TimerTask() {
					@Override
					public void run() {
						Display.getDefault().asyncExec(() -> {
							try {
								CxFindingsHoverControl.super.dispose();
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
	 * Create severity badge with color
	 */
	private void createSeverityBadge() {
		Composite severityComposite = new Composite(mainComposite, SWT.NONE);
		severityComposite.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		GridLayout severityLayout = new GridLayout(2, false);
		severityLayout.marginHeight = 6;
		severityLayout.marginWidth = 8;
		severityLayout.verticalSpacing = 0;
		severityLayout.horizontalSpacing = 8;
		severityComposite.setLayout(severityLayout);
		severityComposite.setBackground(getSeverityColor());

		Label severityIcon = new Label(severityComposite, SWT.NONE);
		severityIcon.setText(getSeverityIcon());
		severityIcon.setForeground(Display.getCurrent().getSystemColor(SWT.COLOR_WHITE));
		severityIcon.setFont(mainComposite.getFont());

		Label severityLabel = new Label(severityComposite, SWT.NONE);
		severityLabel.setText("CHECKMARX FINDING");
		severityLabel.setForeground(Display.getCurrent().getSystemColor(SWT.COLOR_WHITE));
		severityLabel.setFont(mainComposite.getFont());
	}

	/**
	 * Create message/title section
	 */
	private void createMessageSection() {
		Label messageLabel = new Label(mainComposite, SWT.WRAP);
		messageLabel.setText(annotation.getTitle() != null ? annotation.getTitle() : "Security Issue Detected");
		messageLabel.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		messageLabel.setForeground(Display.getCurrent().getSystemColor(SWT.COLOR_BLACK));

		// Description if available
		if (annotation.getDescription() != null && !annotation.getDescription().isEmpty()) {
			Label descriptionLabel = new Label(mainComposite, SWT.WRAP);
			descriptionLabel.setText(annotation.getDescription());
			GridData descData = new GridData(SWT.FILL, SWT.FILL, true, true);
			descData.widthHint = 300;
			descriptionLabel.setLayoutData(descData);
			descriptionLabel.setForeground(Display.getCurrent().getSystemColor(SWT.COLOR_DARK_GRAY));
		}
	}

	/**
	 * Create action buttons
	 */
	private void createActionButtonsSection() {
		Composite buttonComposite = new Composite(mainComposite, SWT.NONE);
		buttonComposite.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		GridLayout buttonLayout = new GridLayout(2, true);
		buttonLayout.marginHeight = 4;
		buttonLayout.marginWidth = 0;
		buttonLayout.horizontalSpacing = 6;
		buttonComposite.setLayout(buttonLayout);

		// Ignore Button
		Button ignoreBtn = new Button(buttonComposite, SWT.PUSH);
		ignoreBtn.setText("Ignore");
		ignoreBtn.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		ignoreBtn.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				System.out.println("[FINDINGS-HOVER] Ignore clicked for: " + annotation.getTitle());
				dispose();
			}
		});

		// Details Button
		Button detailsBtn = new Button(buttonComposite, SWT.PUSH);
		detailsBtn.setText("Details");
		detailsBtn.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		detailsBtn.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				System.out.println("[FINDINGS-HOVER] Details clicked for: " + annotation.getTitle());
				dispose();
			}
		});
	}

	/**
	 * Get severity color from annotation type
	 */
	private Color getSeverityColor() {
		Display display = Display.getCurrent();
		String annotationType = annotation.getType();

		if (annotationType != null) {
			if (annotationType.contains("critical")) {
				return display.getSystemColor(SWT.COLOR_RED);
			} else if (annotationType.contains("high")) {
				return display.getSystemColor(SWT.COLOR_DARK_RED);
			} else if (annotationType.contains("medium")) {
				return display.getSystemColor(SWT.COLOR_DARK_YELLOW);
			}
		}

		return display.getSystemColor(SWT.COLOR_DARK_BLUE);
	}

	/**
	 * Get severity icon emoji
	 */
	private String getSeverityIcon() {
		String annotationType = annotation.getType();
		if (annotationType != null) {
			if (annotationType.contains("critical")) {
				return "🔴";
			} else if (annotationType.contains("high")) {
				return "🟠";
			} else if (annotationType.contains("medium")) {
				return "🟡";
			}
		}
		return "🔵";
	}
}
