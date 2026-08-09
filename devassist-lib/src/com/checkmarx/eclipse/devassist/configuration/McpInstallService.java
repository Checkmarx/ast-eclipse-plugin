package com.checkmarx.eclipse.devassist.configuration;

import java.util.concurrent.CompletableFuture;

import com.checkmarx.eclipse.common.properties.Preferences;
import com.checkmarx.eclipse.common.runner.TenantSettingsProvider;
import com.checkmarx.eclipse.common.utils.CxLogger;


/**
 * MCP Installation Service for Eclipse plugin.
 *
 * Responsible for:
 * - Auto-installing MCP configuration on plugin startup
 * - Validating authentication and MCP tenant settings
 * - Asynchronous background MCP setup
 * - Comprehensive logging
 *
 * Follows the JetBrains implementation pattern for consistency.
 */
public final class McpInstallService {

	private static final String LOG_TAG = "[MCP-INSTALL]";

	private McpInstallService() {
		// Utility class
	}

	/**
	 * Conditionally installs MCP configuration if user is authenticated
	 * and MCP is enabled for their tenant.
	 *
	 * Conditions checked:
	 * - User is authenticated (API key configured)
	 * - AI MCP server flag is enabled in tenant settings
	 * - A credential token is available
	 *
	 * If any condition fails, installation is silently skipped.
	 */
	public static void attemptAutoInstall() {
		CxLogger.info(LOG_TAG + " Attempting auto-install of MCP configuration...");

		try {
			String apiKey = Preferences.getApiKey();
			String additionalParams = Preferences.getAdditionalOptions();

			if (apiKey == null || apiKey.isBlank()) {
				CxLogger.info(LOG_TAG + " Skipping MCP auto-install: user not authenticated (no API key)");
				return;
			}

			attemptAutoInstall(apiKey, additionalParams);
		} catch (Exception e) {
			CxLogger.error(LOG_TAG + " Unexpected error during auto-install attempt: " + e.getMessage(), e);
		}
	}

	/**
	 * Conditionally installs MCP configuration with provided credentials.
	 *
	 * Used when API key is freshly authenticated but not yet persisted to preferences.
	 * Same conditions as attemptAutoInstall() but accepts credentials as parameters.
	 *
	 * @param apiKey API key from authentication (may not be persisted yet)
	 * @param additionalParams Additional params for Checkmarx API
	 */
	public static void attemptAutoInstall(String apiKey, String additionalParams) {
		CxLogger.info(LOG_TAG + " Attempting auto-install of MCP configuration...");

		try {
			if (apiKey == null || apiKey.isBlank()) {
				CxLogger.info(LOG_TAG + " Skipping MCP auto-install: user not authenticated (no API key)");
				return;
			}

		CxLogger.info(LOG_TAG + " User is authenticated, checking MCP server flag...");

		// Check if MCP is enabled for tenant
		boolean aiMcpEnabled;
		try {
			aiMcpEnabled = TenantSettingsProvider.INSTANCE.isAiMcpServerEnabled(apiKey, additionalParams);
		} catch (Exception e) {
			CxLogger.warning(LOG_TAG + " Failed to check MCP server status, skipping: " + e.getMessage());
			return;
		}

		if (!aiMcpEnabled) {
			CxLogger.info(LOG_TAG + " Skipping MCP auto-install: AI MCP server disabled for tenant");
			return;
		}

		CxLogger.info(LOG_TAG + " ✓ All conditions met, installing MCP asynchronously...");

		// Install in background without blocking
		installSilentlyAsync(apiKey);

	} catch (Exception e) {
		CxLogger.error(LOG_TAG + " Unexpected error during auto-install attempt: " + e.getMessage(), e);
	}
}

	/**
	 * Asynchronously installs MCP configuration without user notifications.
	 * Failures are logged but do not interrupt plugin startup.
	 *
	 * @param credential API key for Copilot MCP Authorization header
	 * @return future resolving to Boolean (true=changed, false=unchanged, null=error)
	 */
	public static CompletableFuture<Boolean> installSilentlyAsync(String credential) {
		if (credential == null || credential.isBlank()) {
			CxLogger.info(LOG_TAG + " Cannot install: credential is null or empty");
			return CompletableFuture.completedFuture(false);
		}

		return CompletableFuture.supplyAsync(() -> {
			try {
				CxLogger.info(LOG_TAG + " Background thread started, installing MCP...");
				boolean changed = McpSettingsInjector.installForCopilot(credential);

				if (changed) {
					CxLogger.info(LOG_TAG + " ✓ MCP installation completed successfully (config modified)");
				} else {
					CxLogger.info(LOG_TAG + " MCP installation completed (config unchanged)");
				}

				return changed;
			} catch (Exception ex) {
				CxLogger.error(LOG_TAG + " Background MCP installation failed: " + ex.getMessage(), ex);
				return null; // null signals failure
			}
		});
	}

	/**
	 * Uninstalls MCP configuration. Called during plugin cleanup.
	 *
	 * @return true if MCP entry was removed, false if not found
	 */
	public static boolean uninstall() {
		CxLogger.info(LOG_TAG + " Uninstalling MCP configuration...");

		try {
			boolean removed = McpSettingsInjector.uninstallFromCopilot();

			if (removed) {
				CxLogger.info(LOG_TAG + " ✓ MCP configuration uninstalled successfully");
			} else {
				CxLogger.info(LOG_TAG + " No MCP configuration found to uninstall");
			}

			return removed;
		} catch (Exception e) {
			CxLogger.error(LOG_TAG + " Failed to uninstall MCP: " + e.getMessage(), e);
			return false;
		}
	}
}
