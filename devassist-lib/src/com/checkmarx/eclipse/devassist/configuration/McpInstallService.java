package com.checkmarx.eclipse.devassist.configuration;

import java.util.concurrent.CompletableFuture;

import com.checkmarx.eclipse.common.listener.IMcpInstallCallback;
import com.checkmarx.eclipse.common.listener.IMcpUninstallCallback;
import com.checkmarx.eclipse.common.preferences.Preferences;
import com.checkmarx.eclipse.common.runner.TenantSettingsProvider;
import com.checkmarx.eclipse.common.utils.CxLogger;
import com.checkmarx.eclipse.common.utils.PluginConstants;


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
	private static boolean authListenerRegistered = false;

	private McpInstallService() {
		// Utility class
	}

	static {
		// Register authentication handlers on class load
		registerAuthenticationHandlers();
	}

	private static void registerAuthenticationHandlers() {
		if (!authListenerRegistered) {
			// Register listener for MCP auto-install on API key change
			Preferences.STORE.addPropertyChangeListener(new AuthenticationListener());

			// Register handler for post-authentication UI (welcome dialog, workspace scan)
			Preferences.setAuthenticationSuccessHandler(new AuthenticationSuccessHandler());

			// Register handler so common-lib preference pages (e.g. CheckmarxPreferencePage)
			// can trigger MCP install without depending on this bundle directly.
			Preferences.setMcpInstallHandler(McpInstallService::installFromUi);

			// Register handler so common-lib preference pages (e.g. PreferencesPage)
			// can trigger MCP uninstall on logout without depending on this bundle directly.
			Preferences.setMcpUninstallHandler(McpInstallService::uninstallFromUi);

			authListenerRegistered = true;
			CxLogger.info(LOG_TAG + " Authentication handlers registered");
		}
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
			if (!Preferences.isAuthenticated()) {
				CxLogger.info(LOG_TAG + " Skipping MCP auto-install: user not authenticated");
				return;
			}

			String apiKey = Preferences.getApiKey();
			String additionalParams = Preferences.getAdditionalOptions();

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
	 * Installs MCP configuration in response to a user-initiated action (the "Install MCP"
	 * link on CheckmarxPreferencePage), reporting the outcome to {@code callback} instead of
	 * only logging it - unlike {@link #attemptAutoInstall()}, which is silent by design.
	 *
	 * @param callback notified of success or failure; may be called from a background thread
	 */
	public static void installFromUi(IMcpInstallCallback callback) {
		CxLogger.info(LOG_TAG + " Install MCP requested from preferences page...");

		try {
			if (!Preferences.isAuthenticated()) {
				callback.onFailure(PluginConstants.MCP_NOT_AUTHENTICATED_MESSAGE);
				return;
			}

			String apiKey = Preferences.getApiKey();
			String additionalParams = Preferences.getAdditionalOptions();

			if (apiKey == null || apiKey.isBlank()) {
				callback.onFailure(PluginConstants.MCP_NOT_AUTHENTICATED_MESSAGE);
				return;
			}

			boolean aiMcpEnabled;
			try {
				aiMcpEnabled = TenantSettingsProvider.INSTANCE.isAiMcpServerEnabled(apiKey, additionalParams);
			} catch (Exception e) {
				CxLogger.error(LOG_TAG + " Failed to check MCP server status: " + e.getMessage(), e);
				callback.onFailure(PluginConstants.MCP_INSTALL_GENERIC_FAILURE_MESSAGE);
				return;
			}

			if (!aiMcpEnabled) {
				callback.onFailure(PluginConstants.MCP_NOT_ENABLED_FOR_TENANT_MESSAGE);
				return;
			}

			installSilentlyAsync(apiKey).thenAccept(changed -> {
				if (changed == null) {
					CxLogger.info(LOG_TAG + " Install MCP (from preferences page) failed");
					callback.onFailure(PluginConstants.MCP_INSTALL_GENERIC_FAILURE_MESSAGE);
				} else if (changed) {
					CxLogger.info(LOG_TAG + " Install MCP (from preferences page) succeeded");
					callback.onSuccess();
				} else {
					CxLogger.info(LOG_TAG + " Install MCP (from preferences page): already up to date");
					callback.onAlreadyUpToDate();
				}
			});
		} catch (Exception e) {
			CxLogger.error(LOG_TAG + " Unexpected error while installing MCP from preferences page: " + e.getMessage(), e);
			callback.onFailure(PluginConstants.MCP_INSTALL_GENERIC_FAILURE_MESSAGE);
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
			} catch (Throwable ex) {
				// Catches Throwable, not just Exception: a class-loading failure (e.g.
				// NoClassDefFoundError/LinkageError) inside McpSettingsInjector is an Error,
				// which a plain "catch (Exception)" would miss - and since this future is
				// never joined/observed by the caller, an uncaught Error here would otherwise
				// vanish silently with no log at all.
				logBackgroundFailure(ex);
				return null; // null signals failure
			}
		}).exceptionally(ex -> {
			// Safety net in case something fails outside the try/catch above
			// (e.g. the executor itself, or the catch block's own logging call).
			logBackgroundFailure(ex);
			return null;
		});
	}

	/**
	 * Logs a background MCP installation failure, preserving the original
	 * stack trace even when the failure is an Error rather than an Exception.
	 */
	private static void logBackgroundFailure(Throwable ex) {
		String msg = LOG_TAG + " Background MCP installation failed: " + ex.getClass().getName() + ": " + ex.getMessage();
		Exception loggable = (ex instanceof Exception) ? (Exception) ex : new RuntimeException(ex);
		CxLogger.error(msg, loggable);
	}

	/**
	 * Uninstalls MCP configuration in response to a user-initiated logout, reporting the
	 * outcome to {@code callback} instead of only logging it - unlike {@link #uninstall()},
	 * which is silent by design.
	 *
	 * @param callback notified of success, not-found, or failure; may be called from a background thread
	 */
	public static void uninstallFromUi(IMcpUninstallCallback callback) {
		CxLogger.info(LOG_TAG + " Uninstall MCP requested after logout...");

		try {
			uninstallSilentlyAsync().thenAccept(removed -> {
				// Marshal callback back to UI thread - uninstallSilentlyAsync completes on a thread pool
				org.eclipse.swt.widgets.Display.getDefault().asyncExec(() -> {
					if (removed == null) {
						CxLogger.info(LOG_TAG + " Uninstall MCP (from logout) failed");
						callback.onFailure(PluginConstants.MCP_INSTALL_GENERIC_FAILURE_MESSAGE);
					} else if (removed) {
						CxLogger.info(LOG_TAG + " Uninstall MCP (from logout) succeeded - entry removed");
						callback.onSuccess();
					} else {
						CxLogger.info(LOG_TAG + " Uninstall MCP (from logout) - no entry found");
						callback.onNotFound();
					}
				});
			});
		} catch (Exception e) {
			CxLogger.error(LOG_TAG + " Unexpected error while uninstalling MCP from logout: " + e.getMessage(), e);
			callback.onFailure(PluginConstants.MCP_INSTALL_GENERIC_FAILURE_MESSAGE);
		}
	}

	/**
	 * Asynchronously uninstalls MCP configuration.
	 *
	 * @return future resolving to Boolean (true=removed, false=not found, null=error)
	 */
	public static CompletableFuture<Boolean> uninstallSilentlyAsync() {
		return CompletableFuture.supplyAsync(() -> {
			try {
				CxLogger.info(LOG_TAG + " Background thread started, uninstalling MCP...");
				return uninstall();
			} catch (Throwable ex) {
				logBackgroundFailure(ex);
				return null; // null signals failure
			}
		}).exceptionally(ex -> {
			logBackgroundFailure(ex);
			return null;
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
