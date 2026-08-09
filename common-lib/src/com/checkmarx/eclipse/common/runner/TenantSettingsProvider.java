package com.checkmarx.eclipse.common.runner;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.checkmarx.ast.wrapper.CxConfig;
import com.checkmarx.ast.wrapper.CxException;
import com.checkmarx.ast.wrapper.CxWrapper;
import com.checkmarx.eclipse.common.utils.CxLogger;

/**
 * Provides tenant-specific settings from the Checkmarx API.
 * Fetches configuration details like MCP enablement status.
 */
public class TenantSettingsProvider {
	private static final Logger log = LoggerFactory.getLogger(TenantSettingsProvider.class);
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
			CxConfig config = CxConfig.builder()
					.apiKey(apiKey)
					.additionalParameters(additionalParams)
					.build();

			CxWrapper wrapper = new CxWrapper(config, log);
			boolean mcpEnabled = wrapper.aiMcpServerEnabled();
			CxLogger.info(String.format("MCP Server Status: %s", mcpEnabled ? "ENABLED" : "DISABLED"));
			return mcpEnabled;
		} catch (IOException | InterruptedException | CxException e) {
			CxLogger.error("Failed to check MCP server status: " + e.getMessage(), e);
			// Default to false on error to be conservative
			return false;
		}
	}
}
