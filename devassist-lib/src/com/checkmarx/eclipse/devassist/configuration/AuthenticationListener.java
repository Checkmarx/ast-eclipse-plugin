package com.checkmarx.eclipse.devassist.configuration;

import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.PropertyChangeEvent;

import com.checkmarx.eclipse.common.properties.Preferences;
import com.checkmarx.eclipse.common.utils.CxLogger;

/**
 * Listens for authentication events and triggers MCP auto-installation.
 *
 * Registered globally to respond to successful authentication by:
 * - Detecting API_KEY changes in preferences
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

		// Trigger MCP auto-install when API key is successfully set
		if (Preferences.API_KEY.equals(event.getProperty())) {
			String newApiKey = (String) event.getNewValue();

			// Only proceed if a key was set (not cleared)
			if (newApiKey != null && !newApiKey.isBlank()) {
				CxLogger.info(LOG_TAG + " API key updated, attempting MCP auto-install...");
				McpInstallService.attemptAutoInstall();
			}
		}
	}
}
