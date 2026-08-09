package com.checkmarx.eclipse.devassist.configuration;

import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Display;

import com.checkmarx.eclipse.common.listener.IAuthenticationSuccessHandler;
import com.checkmarx.eclipse.common.utils.CxLogger;
import com.checkmarx.eclipse.devassist.backend.listener.ProjectLifecycleListener;
import com.checkmarx.eclipse.devassist.ui.preferences.WelcomeDialog;
import com.checkmarx.eclipse.startup.PluginStartup;

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

			// Trigger the same initial OSS/IaC/container workspace scan that runs for
			// already-open projects at plugin launch (PluginStartup.initializeBackendScanners()).
			// A project that was already open before this login never gets that scan
			// otherwise, since ProjectLifecycleListener only scans a project when it
			// *opens* while the user is authenticated - re-run it now that login succeeded.
			ProjectLifecycleListener projectListener = (ProjectLifecycleListener) PluginStartup.getProjectListener();
			if (projectListener != null) {
				CxLogger.info(LOG_TAG + " Triggering workspace OSS/IaC/container scan...");
				projectListener.scanAlreadyOpenProjects();
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
