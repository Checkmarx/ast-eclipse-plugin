package com.checkmarx.eclipse.common.listener;

/**
 * Service for installing the Checkmarx MCP server configuration.
 *
 * Allows preference pages in common-lib (e.g. CheckmarxPreferencePage) to trigger
 * MCP installation without depending on devassist-lib, which owns the actual
 * McpInstallService implementation.
 */
public interface IMcpInstallHandler {

	/**
	 * Installs/updates the Checkmarx MCP server configuration for the currently
	 * authenticated user, reporting the outcome to the given callback.
	 *
	 * @param callback notified of success or failure, possibly from a background thread
	 */
	void installMcp(IMcpInstallCallback callback);
}
