package com.checkmarx.eclipse.common.listener;

/**
 * Service for uninstalling the Checkmarx MCP server configuration.
 *
 * Allows preference pages in common-lib (e.g. PreferencesPage) to trigger
 * MCP uninstallation on logout without depending on devassist-lib, which owns the actual
 * McpInstallService implementation.
 */
public interface IMcpUninstallHandler {

	/**
	 * Uninstalls/removes the Checkmarx MCP server configuration after logout.
	 *
	 * @param callback notified of success or failure, possibly from a background thread
	 */
	void uninstallMcp(IMcpUninstallCallback callback);
}
