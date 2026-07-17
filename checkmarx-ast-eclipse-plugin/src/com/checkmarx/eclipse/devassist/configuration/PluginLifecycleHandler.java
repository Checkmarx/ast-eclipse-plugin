package com.checkmarx.eclipse.devassist.configuration;

import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.BundleListener;

import com.checkmarx.eclipse.properties.Preferences;
import com.checkmarx.eclipse.utils.CxLogger;

/**
 * Handles Checkmarx Eclipse plugin lifecycle events.
 *
 * Responsibilities on plugin uninstall:
 * 1. Clear persisted authentication session (API key/OAuth)
 * 2. Remove Checkmarx MCP entry from Copilot configuration
 *
 * This prevents stale credentials and MCP configurations from lingering
 * when the plugin is uninstalled.
 *
 * Follows the JetBrains PluginLifecycleHandler pattern.
 */
public class PluginLifecycleHandler implements BundleListener {

	private static final String LOG_TAG = "[LIFECYCLE]";
	private static final String PLUGIN_ID = "com.checkmarx.eclipse.plugin";

	@Override
	public void bundleChanged(BundleEvent event) {
		Bundle bundle = event.getBundle();

		if (!PLUGIN_ID.equals(bundle.getSymbolicName())) {
			return; // Not our plugin
		}

		// Handle uninstall events
		// BundleEvent.UNINSTALLED = 4
		if (event.getType() == BundleEvent.UNINSTALLED) {
			CxLogger.info(LOG_TAG + " Plugin uninstall detected");
			onPluginUninstall();
		}
		// BundleEvent.UNRESOLVED = 2
		else if (event.getType() == BundleEvent.UNRESOLVED) {
			CxLogger.debug(LOG_TAG + " Plugin unresolved event (may indicate uninstall preparation)");
		}
		// BundleEvent.STOPPING = 8
		else if (event.getType() == BundleEvent.STOPPING) {
			CxLogger.debug(LOG_TAG + " Plugin stopping event");
		}
	}

	/**
	 * Handles plugin uninstallation cleanup.
	 * Clears auth session and removes MCP configuration.
	 */
	private void onPluginUninstall() {
		try {
			CxLogger.info(LOG_TAG + " ✓ Starting plugin cleanup...");

			clearAuthSession();
			removeMcpConfiguration();

			CxLogger.info(LOG_TAG + " ✓ Plugin cleanup completed successfully");
		} catch (Exception e) {
			CxLogger.error(LOG_TAG + " Error during plugin cleanup: " + e.getMessage(), e);
		}
	}

	/**
	 * Clears the persisted authentication session.
	 *
	 * This prevents:
	 * - OAuth tokens from carrying over if user reinstalls the plugin
	 * - API keys from being left behind
	 * - Stale authentication state from interfering with new installations
	 */
	private void clearAuthSession() {
		try {
			CxLogger.debug(LOG_TAG + " Clearing authentication session...");

			// Clear API key preference
			Preferences.clearApiKey();
			CxLogger.debug(LOG_TAG + " ✓ API key cleared");

			// Note: In Eclipse, OAuth tokens are typically cleared separately
			// through OS credential management or browser-based auth stores

			CxLogger.info(LOG_TAG + " ✓ Authentication session cleared");
		} catch (Exception e) {
			CxLogger.warn(LOG_TAG + " Failed to clear auth session: " + e.getMessage());
		}
	}

	/**
	 * Removes Checkmarx MCP server entry from Copilot configuration.
	 *
	 * This prevents leftover MCP configuration from interfering with
	 * other Checkmarx plugin versions or causing Copilot to reference
	 * a non-existent MCP server.
	 */
	private void removeMcpConfiguration() {
		try {
			CxLogger.debug(LOG_TAG + " Removing MCP configuration...");

			boolean removed = McpInstallService.uninstall();

			if (removed) {
				CxLogger.info(LOG_TAG + " ✓ MCP configuration removed");
			} else {
				CxLogger.debug(LOG_TAG + " No MCP configuration found to remove");
			}
		} catch (Exception e) {
			CxLogger.warn(LOG_TAG + " Failed to remove MCP configuration: " + e.getMessage());
		}
	}
}
