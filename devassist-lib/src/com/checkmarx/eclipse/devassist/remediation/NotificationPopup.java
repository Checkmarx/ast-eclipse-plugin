package com.checkmarx.eclipse.devassist.remediation;

import org.eclipse.mylyn.commons.ui.dialogs.AbstractNotificationPopup;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;

/**
 * Generic class to display notification pop-up (balloon) in Eclipse. This class
 * is used to display a message with a title in a pop-up window.
 */
public class NotificationPopup extends AbstractNotificationPopup {

	private final String message;
	private final String title;

	public NotificationPopup(Display display, String title, String message) {
		super(display);
		this.title = title;
		this.message = message;
	}

	@Override
	protected void createContentArea(Composite parent) {
		Label label = new Label(parent, SWT.WRAP);
		label.setText(message);
		label.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
	}

	@Override
	protected String getPopupShellTitle() {
		return title;
	}
}
