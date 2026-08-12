package com.checkmarx.eclipse.devassist.configuration;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.osgi.service.prefs.BackingStoreException;

import com.checkmarx.eclipse.common.utils.CxLogger;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Injects Checkmarx MCP server configuration into GitHub Copilot for Eclipse.
 *
 * <p>
 * GitHub Copilot for Eclipse (https://github.com/microsoft/copilot-for-eclipse)
 * reads its MCP server list from an Eclipse {@code IEclipsePreferences} node
 * scoped to its UI bundle ({@code com.microsoft.copilot.eclipse.ui}), under the
 * preference key {@code "mcp"} (see {@code LanguageServerSettingManager
 * #syncMcpRegistrationConfiguration}, which calls
 * {@code preferenceStore.getString(Constants.MCP)}). The value is a JSON string
 * containing either {@code {"servers": {...}}} or a bare
 * {@code {"name": {...}}} map, using the same schema as VS Code's
 * {@code mcp.json} (the plugin embeds the same Copilot language server used by
 * VS Code). At the time of writing, Copilot for Eclipse does not yet read a
 * file-based {@code mcp.json} (that support is still an open, unmerged
 * proposal - microsoft/copilot-for-eclipse#127/#128), so the preference store
 * is the only mechanism that actually works against released builds.
 *
 * <p>
 * Writing directly to this preference node (rather than through Copilot's own
 * API, which this plugin does not depend on) is safe and immediate: Copilot's
 * own {@code ScopedPreferenceStore} listens on the same underlying node, so our
 * write is picked up live and re-synced to the language server without
 * requiring a restart.
 *
 * <p>
 * Responsible for:
 * <ul>
 * <li>Merging/removing the Checkmarx MCP server entry in Copilot's "mcp"
 * preference, preserving any other servers already configured there</li>
 * <li>Token validation and URL derivation</li>
 * <li>Logging all operations with aggressive debug info</li>
 * </ul>
 */
public final class McpSettingsInjector {

	private static final String LOG_TAG = "[MCP-INJECTOR]";
	private static final ObjectMapper MAPPER = new ObjectMapper();
	private static final String FALLBACK_BASE = "https://ast-master-components.dev.cxast.net";
	private static final String SERVER_KEY = "checkmarx";
	public static final String MCP_ENDPOINT = "/api/security-mcp/mcp";

	/** Bundle symbolic name of GitHub Copilot for Eclipse's UI plugin. */
	private static final String COPILOT_UI_BUNDLE_ID = "com.microsoft.copilot.eclipse.ui";

	/** Preference key Copilot reads its MCP server JSON from (Constants.MCP). */
	private static final String MCP_PREFERENCE_KEY = "mcp";

	private McpSettingsInjector() {
		// Utility class
	}

	/**
	 * Installs/updates Checkmarx MCP configuration for Copilot.
	 *
	 * @param token API key or JWT token with issuer claim
	 * @return true if config was modified, false if already up-to-date
	 * @throws Exception if installation fails
	 */
	public static boolean installForCopilot(String token) throws Exception {
		CxLogger.info(LOG_TAG + " Starting MCP installation for Copilot...");

		if (token == null || token.isBlank()) {
			CxLogger.warning(LOG_TAG + " Cannot install MCP: token is null or empty");
			return false;
		}

		try {
			String issuer = tryExtractIssuer(token);
			CxLogger.info(LOG_TAG + " Token issuer extracted: " + (issuer != null ? issuer : "null (using fallback)"));

			String baseUrl = deriveBaseUrlFromIssuer(issuer);
			CxLogger.info(LOG_TAG + " Derived base URL: " + baseUrl);

			String mcpUrl = baseUrl + MCP_ENDPOINT;
			CxLogger.info(LOG_TAG + " MCP URL: " + mcpUrl);

			CxLogger.info(LOG_TAG + " Copilot MCP preference node: " + COPILOT_UI_BUNDLE_ID + " / " + MCP_PREFERENCE_KEY);

			boolean changed = mergeCheckmarxServer(mcpUrl, token);

			if (changed) {
				CxLogger.info(LOG_TAG + " MCP configuration installed/updated successfully");
			} else {
				CxLogger.info(LOG_TAG + " MCP configuration unchanged (already up-to-date)");
			}

			return changed;
		} catch (Exception e) {
			CxLogger.error(LOG_TAG + " Failed to install MCP configuration: " + e.getMessage(), e);
			throw e;
		}
	}

	/**
	 * Uninstalls Checkmarx MCP server entry from Copilot configuration.
	 *
	 * @return true if entry was removed, false if not found
	 * @throws Exception if uninstallation fails
	 */
	public static boolean uninstallFromCopilot() throws Exception {
		CxLogger.info(LOG_TAG + " Starting MCP uninstallation...");

		try {
			boolean removed = removeCheckmarxServer();

			if (removed) {
				CxLogger.info(LOG_TAG + " Checkmarx MCP entry removed successfully");
			} else {
				CxLogger.info(LOG_TAG + " No Checkmarx MCP entry found to remove");
			}

			return removed;
		} catch (Exception e) {
			CxLogger.error(LOG_TAG + " Failed to uninstall MCP configuration: " + e.getMessage(), e);
			throw e;
		}
	}

	/**
	 * Merges the Checkmarx server entry into Copilot's "mcp" preference, keeping
	 * any other servers already present. Returns true if the preference value was
	 * modified, false if content unchanged.
	 */
	private static boolean mergeCheckmarxServer(String url, String token) throws BackingStoreException {
		IEclipsePreferences node = InstanceScope.INSTANCE.getNode(COPILOT_UI_BUNDLE_ID);

		CxLogger.info(LOG_TAG + " Reading existing Copilot MCP preference...");
		Map<String, Object> servers = readServers(node);

		Map<String, Object> headers = new LinkedHashMap<>();
		headers.put("cx-origin", "eclipse-plugin");
		headers.put("Authorization", token);

		Map<String, Object> serverEntry = new LinkedHashMap<>();
		serverEntry.put("type", "http");
		serverEntry.put("url", url);
		serverEntry.put("headers", headers);

		Object existing = servers.get(SERVER_KEY);
		boolean changed = !Objects.equals(existing, serverEntry);
		CxLogger.info(LOG_TAG + " Config changed: " + changed);

		if (!changed) {
			CxLogger.info(LOG_TAG + " Existing MCP entry matches new entry exactly");
			return false;
		}

		CxLogger.info(LOG_TAG + " Updating MCP server entry in Copilot preference");
		servers.put(SERVER_KEY, serverEntry);
		writeServers(node, servers);

		CxLogger.info(LOG_TAG + " MCP preference updated for bundle: " + COPILOT_UI_BUNDLE_ID);
		return true;
	}

	/**
	 * Removes the Checkmarx server entry from Copilot's "mcp" preference. Returns
	 * true if the entry was removed, false if not found.
	 */
	private static boolean removeCheckmarxServer() throws BackingStoreException {
		IEclipsePreferences node = InstanceScope.INSTANCE.getNode(COPILOT_UI_BUNDLE_ID);

		CxLogger.info(LOG_TAG + " Reading Copilot MCP preference for removal...");
		Map<String, Object> servers = readServers(node);

		boolean removed = servers.remove(SERVER_KEY) != null;

		if (!removed) {
			CxLogger.info(LOG_TAG + " Checkmarx MCP entry not found in Copilot preference");
			return false;
		}

		CxLogger.info(LOG_TAG + " Checkmarx MCP entry found and removed");
		writeServers(node, servers);

		CxLogger.info(LOG_TAG + " MCP entry removed from bundle preference: " + COPILOT_UI_BUNDLE_ID);
		return true;
	}

	/**
	 * Reads the "mcp" preference value and extracts the servers map. Accepts both
	 * {@code {"servers": {...}}} and bare {@code {"name": {...}}} forms (mirroring
	 * how Copilot itself parses this preference), tolerating a blank or invalid
	 * value by returning an empty, mutable map.
	 */
	@SuppressWarnings("unchecked")
	private static Map<String, Object> readServers(IEclipsePreferences node) {
		String raw = node.get(MCP_PREFERENCE_KEY, "");
		if (raw == null || raw.isBlank()) {
			CxLogger.info(LOG_TAG + " No existing Copilot MCP preference value, starting fresh");
			return new LinkedHashMap<>();
		}

		try {
			Map<String, Object> parsed = MAPPER.readValue(raw, new TypeReference<Map<String, Object>>() {
			});
			if (parsed == null) {
				return new LinkedHashMap<>();
			}

			Object serversObj = parsed.get("servers");
			if (serversObj instanceof Map) {
				CxLogger.info(LOG_TAG + "Existing preference read successfully (wrapped form)");
				return new LinkedHashMap<>((Map<String, Object>) serversObj);
			}

			CxLogger.info(LOG_TAG + "Existing preference read successfully (bare form)");
			return new LinkedHashMap<>(parsed);
		} catch (Exception e) {
			CxLogger.warning(LOG_TAG + " Failed to parse existing Copilot MCP preference, starting fresh: " + e.getMessage());
			return new LinkedHashMap<>();
		}
	}

	/**
	 * Writes the servers map back to the "mcp" preference, wrapped as
	 * {@code {"servers": {...}}}, and flushes it so it is persisted immediately
	 * and observed by Copilot's live preference listeners.
	 */
	private static void writeServers(IEclipsePreferences node, Map<String, Object> servers) throws BackingStoreException {
		try {
			if (servers.isEmpty()) {
				node.remove(MCP_PREFERENCE_KEY);
			} else {
				Map<String, Object> root = new LinkedHashMap<>();
				root.put("servers", servers);
				node.put(MCP_PREFERENCE_KEY, MAPPER.writeValueAsString(root));
			}
			node.flush();
		} catch (BackingStoreException e) {
			throw e;
		} catch (Exception e) {
			throw new IllegalStateException("Failed to serialize Copilot MCP preference", e);
		}
	}

	/**
	 * Extracts the issuer claim from a JWT token.
	 * Token format: header.payload.signature
	 * Payload is base64url encoded JSON containing "iss" claim.
	 */
	private static String tryExtractIssuer(String rawToken) {
		if (rawToken == null || rawToken.isBlank()) {
			CxLogger.info(LOG_TAG + " Token is null or empty");
			return null;
		}

		try {
			String[] parts = rawToken.split("\\.");
			if (parts.length < 2) {
				CxLogger.info(LOG_TAG + " Token does not have expected JWT format (parts=" + parts.length + ")");
				return null;
			}

			CxLogger.info(LOG_TAG + " Decoding JWT payload...");
			byte[] payload = Base64.getUrlDecoder().decode(parts[1]);
			String json = new String(payload, StandardCharsets.UTF_8);

			Map<String, Object> map = MAPPER.readValue(json, new TypeReference<Map<String, Object>>() {
			});
			Object iss = map.get("iss");

			if (iss != null) {
				CxLogger.info(LOG_TAG + "Issuer extracted: " + iss.toString());
				return iss.toString();
			}

			CxLogger.info(LOG_TAG + " No 'iss' claim found in JWT payload");
			return null;
		} catch (Exception e) {
			CxLogger.warning(LOG_TAG + " Failed to parse JWT token: " + e.getMessage());
			return null;
		}
	}

	/**
	 * Derives AST base URL from issuer claim.
	 * If issuer is like https://iam.checkmarx.com, converts to https://ast.checkmarx.com
	 */
	private static String deriveBaseUrlFromIssuer(String issuer) {
		if (issuer == null || issuer.isBlank()) {
			CxLogger.info(LOG_TAG + " Issuer is null/empty, using fallback base URL");
			return FALLBACK_BASE;
		}

		try {
			CxLogger.info(LOG_TAG + " Deriving base URL from issuer: " + issuer);
			String host = URI.create(issuer).getHost();

			if (host != null && host.contains("iam.checkmarx")) {
				String newHost = host.replace("iam", "ast");
				String baseUrl = "https://" + newHost;
				CxLogger.info(LOG_TAG + "Derived base URL: " + baseUrl);
				return baseUrl;
			}

			CxLogger.info(LOG_TAG + " Host does not match iam.checkmarx pattern, using fallback");
			return FALLBACK_BASE;
		} catch (Exception e) {
			CxLogger.warning(LOG_TAG + " Failed to derive base URL from issuer: " + e.getMessage());
			return FALLBACK_BASE;
		}
	}
}
