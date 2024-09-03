package com.checkmarx.eclipse.utils;

import org.eclipse.mylyn.commons.ui.dialogs.AbstractNotificationPopup;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Link;

public class NotificationPopUpUI extends AbstractNotificationPopup {

	private String title;
	private String text;
	private SelectionAdapter textAction;
	private String btnText;
	private SelectionAdapter btnAction;

	public NotificationPopUpUI(Display display, String title, String text, SelectionAdapter textAction, String btnText, SelectionAdapter btnAction) {
		super(display);
		this.title = title;
		this.text = text;
		this.textAction = textAction;
		this.btnText = btnText;
		this.btnAction = btnAction;
	}

	@Override
	protected void createContentArea(Composite parent) {
		Composite container = new Composite(parent, SWT.NULL);
		container.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
		container.setLayout(new GridLayout(1, false));

		Link description = new Link(container, SWT.WRAP | SWT.MULTI);
		description.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		description.setText(text);

		if (textAction != null) {
			description.addSelectionListener(textAction);
		}

		if (btnAction != null) {
			new Label(container, SWT.NONE);

			Link btn = new Link(container, SWT.WRAP | SWT.LEFT);
			btn.setText("<a href=\"\">" + btnText + "</a>");
			GridData linkData = new GridData();
			linkData.horizontalAlignment = SWT.RIGHT;
			btn.setLayoutData(linkData);
			btn.addSelectionListener(btnAction);
		}
	}

	@Override
	protected String getPopupShellTitle() {
		return title;
	}

	@Override
	protected Image getPopupShellImage(int maximumHeight) {
		return null;
	}
}