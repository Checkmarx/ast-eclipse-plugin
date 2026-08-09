package com.checkmarx.eclipse.devassist.configuration;

import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Display;

import com.checkmarx.eclipse.common.listener.IAuthenticationSuccessHandler;
import com.checkmarx.eclipse.common.listener.IWorkspaceScanService;
import com.checkmarx.eclipse.common.properties.Preferences;
import com.checkmarx.eclipse.common.utils.CxLogger;
import com.checkmarx.eclipse.devassist.ui.preferences.WelcomeDialog;

/**
 * Handles post-authentication UI and backend setup in devassist-lib.
 *
 * Triggered when authentication succeeds in PreferencesPage, this handler:
 * - Shows the welcome dialog
 * - Re-enables the logout button
 * - Ensures workspace scans are triggered for open projects
 */
public class AuthenticationSuccessHandler implements IAuthenticationSuccessHandler {

	private static final String LOG_TAG = "[AUTH-SUCCESS]";

	@Override
	public void onAuthenticationSuccess(boolean mcpEnabled, Object logoutButton, String apiKey, String additionalParams) {
		try {
			Button logout = (Button) logoutButton;

			// Trigger workspace scan via service (avoids importing PluginStartup in devassist-lib)
			IWorkspaceScanService scanService = Preferences.getWorkspaceScanService();
			if (scanService != null) {
				scanService.scanWorkspace();
			} else {
				CxLogger.warning(LOG_TAG + " Workspace scan service not available");
			}

			// Show welcome dialog with MCP status
			WelcomeDialog dlg = new WelcomeDialog(
				Display.getDefault().getActiveShell(),
				mcpEnabled);

			// Re-enable Logout right as the welcome dialog is about to appear, so it stays
			// disabled for the entire connect/validate flow and only becomes usable once
			// that flow has visibly completed.
			if (logout != null && !logout.isDisposed()) {
				logout.setEnabled(true);
			}

			dlg.open();
		} catch (Exception ex) {
			CxLogger.error(LOG_TAG + " Failed to show welcome dialog", ex);
			if (logoutButton != null && logoutButton instanceof Button) {
				Button btn = (Button) logoutButton;
				if (!btn.isDisposed()) {
					btn.setEnabled(true);
				}
			}
		}
	}
}
