package com.checkmarx.eclipse.common.listener;

/**
 * Handler for successful authentication events.
 *
 * Allows devassist-lib to respond to successful authentication in PreferencesPage
 * without creating a reverse dependency from common-lib to devassist-lib.
 */
public interface IAuthenticationSuccessHandler {

	/**
	 * Called after successful authentication and credential validation.
	 *
	 * @param mcpEnabled whether AI MCP server is enabled for the tenant
	 * @param logoutButton the logout button (may be disabled during flow)
	 * @param apiKey the newly authenticated API key
	 * @param additionalParams additional parameters for Checkmarx API
	 */
	void onAuthenticationSuccess(boolean mcpEnabled, Object logoutButton, String apiKey, String additionalParams);
}
