package com.checkmarx.eclipse.common.runner;

import com.checkmarx.eclipse.common.utils.CxLogger;
import com.checkmarx.eclipse.common.wrapper.WrapperProvider;

/**
 * Provides tenant-specific settings from the Checkmarx API.
 * Fetches configuration details like MCP enablement status.
 */
public class TenantSettingsProvider {
	private static final String LOG_PREFIX = "[TENANT_SETTINGS_PROVIDER] ";
	public static final TenantSettingsProvider INSTANCE = new TenantSettingsProvider();

	private TenantSettingsProvider() {
	}

	/**
	 * Check if AI MCP (Checkmarx One Assist) is enabled for the current tenant
	 *
	 * @param apiKey API key for authentication
	 * @param additionalParams Additional parameters for the CxWrapper
	 * @return true if MCP is enabled, false otherwise
	 */
	public boolean isAiMcpServerEnabled(String apiKey, String additionalParams) {
		if (apiKey == null || apiKey.trim().isEmpty()) {
			return false;
		}
		try {
			boolean mcpEnabled = new WrapperProvider().isAiMcpServerEnabled(apiKey, additionalParams);
			CxLogger.info(String.format("MCP Server Status: %s", mcpEnabled ? "ENABLED" : "DISABLED"));
			return mcpEnabled;
		} catch (Exception e) {
			CxLogger.error(String.format("%s Failed to check MCP server status: %s", LOG_PREFIX, e.getMessage()), e);
			// Default to false on error to be conservative
			return false;
		}
	}
}
