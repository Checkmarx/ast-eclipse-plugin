package com.checkmarx.eclipse.devassist.remediation;

import java.net.MalformedURLException;
import java.net.URL;

import org.eclipse.mylyn.commons.ui.dialogs.AbstractNotificationPopup;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Link;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;

import com.checkmarx.eclipse.common.utils.CxLogger;

/**
 * Notification popup prompting the user to install GitHub Copilot for Eclipse, with a
 * clickable link that opens its Eclipse Marketplace listing in the system browser.
 * <p>
 * Unlike {@link NotificationPopup}, this popup does not auto-close: installing a plugin
 * takes the user out of Eclipse, so it stays visible (with its standard close control) until
 * dismissed.
 */
public class CopilotInstallNotificationPopup extends AbstractNotificationPopup {

	private final String title;
	private final String message;
	private final String marketplaceUrl;

	public CopilotInstallNotificationPopup(Display display, String title, String message, String marketplaceUrl) {
		super(display);
		this.title = title;
		this.message = message;
		this.marketplaceUrl = marketplaceUrl;
	}

	@Override
	protected void createContentArea(Composite parent) {
		Label label = new Label(parent, SWT.WRAP);
		label.setText(message);
		label.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

		Link link = new Link(parent, SWT.NONE);
		link.setText("<a>Open Eclipse Marketplace</a>");
		link.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		link.addListener(SWT.Selection, event -> openMarketplace());
	}

	private void openMarketplace() {
		try {
			PlatformUI.getWorkbench().getBrowserSupport().getExternalBrowser().openURL(new URL(marketplaceUrl));
		} catch (PartInitException | MalformedURLException e) {
			CxLogger.error("Failed to open Eclipse Marketplace link: " + e.getMessage(), e);
		}
	}

	@Override
	protected String getPopupShellTitle() {
		return title;
	}
}
