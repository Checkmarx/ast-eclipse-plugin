package com.checkmarx.eclipse.devassist.configuration;

import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.PropertyChangeEvent;

import com.checkmarx.eclipse.common.preferences.Preferences;
import com.checkmarx.eclipse.common.utils.CxLogger;

/**
 * Listens for authentication events and triggers MCP auto-installation.
 *
 * Registered globally to respond to successful authentication by:
 * - Detecting the CREDENTIALS_VALIDATED flag turning true - this fires regardless of which
 *   credential type (API key today, OAuth in future) produced the successful login, unlike
 *   listening for API_KEY changes directly.
 * - Triggering MCP configuration installation
 * - Logging success/failure for debugging
 */
public class AuthenticationListener implements IPropertyChangeListener {

	private static final String LOG_TAG = "[AUTH-LISTENER]";

	@Override
	public void propertyChange(PropertyChangeEvent event) {
		if (event == null || event.getProperty() == null) {
			return;
		}

		// Trigger MCP auto-install when authentication just succeeded
		if (Preferences.CREDENTIALS_VALIDATED.equals(event.getProperty())) {
			Object newValue = event.getNewValue();
			boolean nowValidated = newValue instanceof Boolean && (Boolean) newValue;

			if (nowValidated) {
				CxLogger.info(LOG_TAG + " Authentication succeeded, attempting MCP auto-install...");
				McpInstallService.attemptAutoInstall();
			}
		}
	}
}
