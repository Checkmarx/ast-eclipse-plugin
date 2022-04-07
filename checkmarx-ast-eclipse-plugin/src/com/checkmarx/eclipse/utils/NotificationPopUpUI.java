package com.checkmarx.eclipse.utils;

import java.net.MalformedURLException;
import java.net.URL;

import org.eclipse.mylyn.commons.ui.dialogs.AbstractNotificationPopup;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Link;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;

public class NotificationPopUpUI extends AbstractNotificationPopup {
	
	private String title;
	private String text;

	public NotificationPopUpUI(Display display, String title, String text) {
		super(display);
		this.title = title;
		this.text = text;
	}

	@Override
	protected void createContentArea(Composite composite) {
		composite.setLayout(new GridLayout(1, false));
		
		GridData layoutData = new GridData(GridData.FILL, GridData.BEGINNING,true, false, 2, 1);
	
		Link link = new Link(composite, SWT.WRAP | SWT.MULTI);
		link.setLayoutData(layoutData);
		link.setText(text);
		link.setSize(400, 100);
		link.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent event) {
				try {
					PlatformUI.getWorkbench().getBrowserSupport().getExternalBrowser().openURL(new URL(event.text));
				} catch (PartInitException | MalformedURLException e) {
					CxLogger.error(String.format(PluginConstants.ERROR_GETTING_CODEBASHING_DETAILS, e.getMessage()), e);
				}
			}
		});
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