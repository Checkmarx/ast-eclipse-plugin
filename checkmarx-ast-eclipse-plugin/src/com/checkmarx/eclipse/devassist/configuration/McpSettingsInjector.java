package com.checkmarx.eclipse.devassist.configuration;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import com.checkmarx.eclipse.utils.CxLogger;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Injects Checkmarx MCP server configuration into GitHub Copilot's MCP settings file.
 *
 * Responsible for:
 * - Reading/writing the Copilot MCP config file (~/github-copilot/intellij/mcp.json)
 * - Merging/removing Checkmarx MCP server entry
 * - Token validation and URL derivation
 * - Logging all operations with aggressive debug info
 */
public final class McpSettingsInjector {

	private static final String LOG_TAG = "[MCP-INJECTOR]";
	private static final ObjectMapper MAPPER = new ObjectMapper();
	private static final String FALLBACK_BASE = "https://ast-master-components.dev.cxast.net";
	private static final String SERVER_KEY = "checkmarx-mcp";

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
			CxLogger.warn(LOG_TAG + " Cannot install MCP: token is null or empty");
			return false;
		}

		try {
			String issuer = tryExtractIssuer(token);
			CxLogger.debug(LOG_TAG + " Token issuer extracted: " + (issuer != null ? issuer : "null (using fallback)"));

			String baseUrl = deriveBaseUrlFromIssuer(issuer);
			CxLogger.debug(LOG_TAG + " Derived base URL: " + baseUrl);

			String mcpUrl = baseUrl + "/api/security-mcp/mcp";
			CxLogger.info(LOG_TAG + " MCP URL: " + mcpUrl);

			Path cfg = resolveCopilotMcpConfigPath();
			CxLogger.info(LOG_TAG + " Copilot MCP config path: " + cfg.toAbsolutePath());

			boolean changed = mergeCheckmarxServer(cfg, mcpUrl, token);

			if (changed) {
				CxLogger.info(LOG_TAG + " ✓ MCP configuration installed/updated successfully");
			} else {
				CxLogger.debug(LOG_TAG + " MCP configuration unchanged (already up-to-date)");
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
			Path cfg = resolveCopilotMcpConfigPath();
			CxLogger.info(LOG_TAG + " Copilot MCP config path: " + cfg.toAbsolutePath());

			boolean removed = removeCheckmarxServer(cfg);

			if (removed) {
				CxLogger.info(LOG_TAG + " ✓ Checkmarx MCP entry removed successfully");
			} else {
				CxLogger.debug(LOG_TAG + " No Checkmarx MCP entry found to remove");
			}

			return removed;
		} catch (Exception e) {
			CxLogger.error(LOG_TAG + " Failed to uninstall MCP configuration: " + e.getMessage(), e);
			throw e;
		}
	}

	/**
	 * Resolves the platform-specific Copilot MCP configuration file path.
	 *
	 * Uses Eclipse-specific subdirectory to avoid conflicts with JetBrains IDEs:
	 * Windows: %LOCALAPPDATA%/github-copilot/eclipse/mcp.json
	 * macOS/Linux: ~/.config/github-copilot/eclipse/mcp.json
	 */
	private static Path resolveCopilotMcpConfigPath() {
		String os = System.getProperty("os.name").toLowerCase(Locale.ENGLISH);
		String home = System.getProperty("user.home");

		CxLogger.debug(LOG_TAG + " Resolving MCP config path for OS: " + os);
		CxLogger.debug(LOG_TAG + " Using Eclipse-specific path (not JetBrains 'intellij' folder)");

		if (os.contains("win")) {
			String localAppData = System.getenv("LOCALAPPDATA");
			if (localAppData == null || localAppData.isBlank()) {
				throw new IllegalStateException("%LOCALAPPDATA% environment variable not set on Windows");
			}
			Path path = Paths.get(localAppData, "github-copilot", "eclipse", "mcp.json");
			CxLogger.debug(LOG_TAG + " Windows config path: " + path.toAbsolutePath());
			return path;
		}

		// macOS and Linux
		String xdgConfig = System.getenv("XDG_CONFIG_HOME");
		if (xdgConfig != null && !xdgConfig.isBlank()) {
			Path path = Paths.get(xdgConfig, "github-copilot", "eclipse", "mcp.json");
			CxLogger.debug(LOG_TAG + " XDG_CONFIG_HOME path: " + path.toAbsolutePath());
			return path;
		}

		// Fallback to ~/.config
		Path path = Paths.get(home, ".config", "github-copilot", "eclipse", "mcp.json");
		CxLogger.debug(LOG_TAG + " Fallback config path: " + path.toAbsolutePath());
		return path;
	}

	/**
	 * Merges Checkmarx server entry into MCP config file.
	 * Returns true if file was modified, false if content unchanged.
	 */
	@SuppressWarnings("unchecked")
	private static boolean mergeCheckmarxServer(Path configPath, String url, String token) throws Exception {
		CxLogger.debug(LOG_TAG + " Reading existing MCP config...");
		Map<String, Object> root = readJson(configPath);

		Map<String, Object> servers = (Map<String, Object>) root
				.getOrDefault("servers", new LinkedHashMap<>());

		Map<String, Object> headers = new LinkedHashMap<>();
		headers.put("cx-origin", "eclipse-plugin");
		headers.put("Authorization", token);

		Map<String, Object> serverEntry = new LinkedHashMap<>();
		serverEntry.put("url", url);

		Map<String, Object> requestInit = new LinkedHashMap<>();
		requestInit.put("headers", headers);
		serverEntry.put("requestInit", requestInit);

		String mcpServerKey = SERVER_KEY;
		Map<String, Object> existing = (Map<String, Object>) servers.get(mcpServerKey);

		boolean changed = !Objects.equals(existing, serverEntry);
		CxLogger.debug(LOG_TAG + " Config changed: " + changed);

		if (!changed) {
			CxLogger.debug(LOG_TAG + " Existing MCP entry matches new entry exactly");
			return false;
		}

		CxLogger.debug(LOG_TAG + " Updating MCP server entry in config");
		servers.put(mcpServerKey, serverEntry);
		root.put("servers", servers);

		// Create parent directories and write file
		Files.createDirectories(configPath.getParent());
		Files.writeString(configPath,
				MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(root),
				StandardCharsets.UTF_8);

		CxLogger.info(LOG_TAG + " ✓ MCP config written to: " + configPath.toAbsolutePath());
		return true;
	}

	/**
	 * Removes Checkmarx server entry from MCP config file.
	 * Returns true if entry was removed, false if not found.
	 */
	@SuppressWarnings("unchecked")
	private static boolean removeCheckmarxServer(Path configPath) throws Exception {
		if (!Files.exists(configPath)) {
			CxLogger.debug(LOG_TAG + " Config file does not exist: " + configPath.toAbsolutePath());
			return false;
		}

		CxLogger.debug(LOG_TAG + " Reading MCP config for removal...");
		Map<String, Object> root = readJson(configPath);
		Object serversObj = root.get("servers");

		if (!(serversObj instanceof Map)) {
			CxLogger.debug(LOG_TAG + " 'servers' field not found or is not a map");
			return false;
		}

		String mcpServerKey = SERVER_KEY;
		Map<String, Object> servers = (Map<String, Object>) serversObj;

		boolean removed = servers.remove(mcpServerKey) != null;

		if (!removed) {
			CxLogger.debug(LOG_TAG + " Checkmarx MCP entry not found in config");
			return false;
		}

		CxLogger.debug(LOG_TAG + " Checkmarx MCP entry found and removed");
		root.put("servers", servers);
		Files.writeString(configPath,
				MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(root),
				StandardCharsets.UTF_8);

		CxLogger.info(LOG_TAG + " ✓ MCP entry removed from: " + configPath.toAbsolutePath());
		return true;
	}

	/**
	 * Reads JSON from config file, returns empty root if file doesn't exist.
	 */
	@SuppressWarnings("unchecked")
	private static Map<String, Object> readJson(Path path) throws Exception {
		if (!Files.exists(path)) {
			CxLogger.debug(LOG_TAG + " Config file does not exist, creating new");
			return emptyServersRoot();
		}

		try {
			String content = stripLineComments(Files.readString(path, StandardCharsets.UTF_8));
			Map<String, Object> map = MAPPER.readValue(content, new TypeReference<Map<String, Object>>() {
			});
			if (map == null || map.isEmpty()) {
				CxLogger.debug(LOG_TAG + " Config file is empty or null, using empty root");
				return emptyServersRoot();
			}
			CxLogger.debug(LOG_TAG + " ✓ Config file read successfully");
			return map;
		} catch (Exception e) {
			CxLogger.warn(LOG_TAG + " Failed to read existing config, starting fresh: " + e.getMessage());
			return emptyServersRoot();
		}
	}

	/**
	 * Returns a new root map with empty servers.
	 */
	private static Map<String, Object> emptyServersRoot() {
		Map<String, Object> root = new LinkedHashMap<>();
		root.put("servers", new LinkedHashMap<>());
		return root;
	}

	/**
	 * Strips single-line comments from JSON content.
	 */
	private static String stripLineComments(String s) {
		return s.replaceAll("(?m)^\\s*//.*$", "");
	}

	/**
	 * Extracts the issuer claim from a JWT token.
	 * Token format: header.payload.signature
	 * Payload is base64url encoded JSON containing "iss" claim.
	 */
	private static String tryExtractIssuer(String rawToken) {
		if (rawToken == null || rawToken.isBlank()) {
			CxLogger.debug(LOG_TAG + " Token is null or empty");
			return null;
		}

		try {
			String[] parts = rawToken.split("\\.");
			if (parts.length < 2) {
				CxLogger.debug(LOG_TAG + " Token does not have expected JWT format (parts=" + parts.length + ")");
				return null;
			}

			CxLogger.debug(LOG_TAG + " Decoding JWT payload...");
			byte[] payload = Base64.getUrlDecoder().decode(parts[1]);
			String json = new String(payload, StandardCharsets.UTF_8);

			Map<String, Object> map = MAPPER.readValue(json, new TypeReference<Map<String, Object>>() {
			});
			Object iss = map.get("iss");

			if (iss != null) {
				CxLogger.debug(LOG_TAG + " ✓ Issuer extracted: " + iss.toString());
				return iss.toString();
			}

			CxLogger.debug(LOG_TAG + " No 'iss' claim found in JWT payload");
			return null;
		} catch (Exception e) {
			CxLogger.warn(LOG_TAG + " Failed to parse JWT token: " + e.getMessage());
			return null;
		}
	}

	/**
	 * Derives AST base URL from issuer claim.
	 * If issuer is like https://iam.checkmarx.com, converts to https://ast.checkmarx.com
	 */
	private static String deriveBaseUrlFromIssuer(String issuer) {
		if (issuer == null || issuer.isBlank()) {
			CxLogger.debug(LOG_TAG + " Issuer is null/empty, using fallback base URL");
			return FALLBACK_BASE;
		}

		try {
			CxLogger.debug(LOG_TAG + " Deriving base URL from issuer: " + issuer);
			String host = URI.create(issuer).getHost();

			if (host != null && host.contains("iam.checkmarx")) {
				String newHost = host.replace("iam", "ast");
				String baseUrl = "https://" + newHost;
				CxLogger.info(LOG_TAG + " ✓ Derived base URL: " + baseUrl);
				return baseUrl;
			}

			CxLogger.debug(LOG_TAG + " Host does not match iam.checkmarx pattern, using fallback");
			return FALLBACK_BASE;
		} catch (Exception e) {
			CxLogger.warn(LOG_TAG + " Failed to derive base URL from issuer: " + e.getMessage());
			return FALLBACK_BASE;
		}
	}

	/**
	 * Public accessor for the MCP config path (used by UI).
	 */
	public static Path getMcpJsonPath() {
		return resolveCopilotMcpConfigPath();
	}
}
