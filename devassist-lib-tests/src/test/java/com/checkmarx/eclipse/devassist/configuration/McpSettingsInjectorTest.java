package com.checkmarx.eclipse.devassist.configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Unit tests for {@link McpSettingsInjector}. This writes to a real
 * {@code IEclipsePreferences} node (Copilot for Eclipse's UI bundle
 * preference scope) - there's no network I/O involved, just local preference
 * store read/write, so this is safe to exercise directly. The "mcp" key on
 * that node is cleared before and after every test for isolation.
 */
class McpSettingsInjectorTest {

	private static final String COPILOT_UI_BUNDLE_ID = "com.microsoft.copilot.eclipse.ui";
	private static final String MCP_PREFERENCE_KEY = "mcp";
	private static final ObjectMapper MAPPER = new ObjectMapper();

	private IEclipsePreferences node;

	@BeforeEach
	void clearPreferenceBefore() throws Exception {
		node = InstanceScope.INSTANCE.getNode(COPILOT_UI_BUNDLE_ID);
		node.remove(MCP_PREFERENCE_KEY);
		node.flush();
	}

	@AfterEach
	void clearPreferenceAfter() throws Exception {
		node.remove(MCP_PREFERENCE_KEY);
		node.flush();
	}

	private String jwtWithIssuer(String issuer) {
		String payloadJson = "{\"iss\":\"" + issuer + "\"}";
		String payload = Base64.getUrlEncoder().withoutPadding()
				.encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8));
		return "header." + payload + ".signature";
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> readRawServers() throws Exception {
		String raw = node.get(MCP_PREFERENCE_KEY, "");
		if (raw.isBlank()) {
			return new LinkedHashMap<>();
		}
		Map<String, Object> parsed = MAPPER.readValue(raw, Map.class);
		Object servers = parsed.get("servers");
		return servers instanceof Map ? (Map<String, Object>) servers : parsed;
	}

	@Test
	@DisplayName("installForCopilot returns false and writes nothing for a null or blank token")
	void installForCopilotRejectsBlankToken() throws Exception {
		assertFalse(McpSettingsInjector.installForCopilot(null));
		assertFalse(McpSettingsInjector.installForCopilot("  "));
		assertTrue(readRawServers().isEmpty());
	}

	@Test
	@DisplayName("installForCopilot derives the base URL from an iam.checkmarx.* JWT issuer")
	void installForCopilotDerivesBaseUrlFromIssuer() throws Exception {
		String token = jwtWithIssuer("https://iam.checkmarx.com");

		boolean changed = McpSettingsInjector.installForCopilot(token);

		assertTrue(changed);
		Map<String, Object> servers = readRawServers();
		assertTrue(servers.containsKey("checkmarx"));
		@SuppressWarnings("unchecked")
		Map<String, Object> entry = (Map<String, Object>) servers.get("checkmarx");
		assertEquals("https://ast.checkmarx.com" + McpSettingsInjector.MCP_ENDPOINT, entry.get("url"));
	}

	@Test
	@DisplayName("installForCopilot falls back to the default base URL for a non-JWT token")
	void installForCopilotFallsBackForNonJwtToken() throws Exception {
		boolean changed = McpSettingsInjector.installForCopilot("not-a-jwt-token");

		assertTrue(changed);
		Map<String, Object> servers = readRawServers();
		@SuppressWarnings("unchecked")
		Map<String, Object> entry = (Map<String, Object>) servers.get("checkmarx");
		assertTrue(((String) entry.get("url")).contains(McpSettingsInjector.MCP_ENDPOINT));
	}

	@Test
	@DisplayName("Installing the exact same token twice reports no change the second time")
	void installForCopilotIsIdempotentForSameToken() throws Exception {
		String token = jwtWithIssuer("https://iam.checkmarx.com");

		assertTrue(McpSettingsInjector.installForCopilot(token));
		assertFalse(McpSettingsInjector.installForCopilot(token), "Second install with the identical token should be a no-op");
	}

	@Test
	@DisplayName("Installing a different token after an existing install reports a change")
	void installForCopilotDetectsTokenChange() throws Exception {
		McpSettingsInjector.installForCopilot(jwtWithIssuer("https://iam.checkmarx.com"));

		boolean changed = McpSettingsInjector.installForCopilot(jwtWithIssuer("https://iam.checkmarx.com") + "-different");

		assertTrue(changed);
	}

	@Test
	@DisplayName("installForCopilot preserves other server entries already present in the preference")
	void installForCopilotPreservesOtherServers() throws Exception {
		Map<String, Object> existing = new LinkedHashMap<>();
		existing.put("other-server", Map.of("type", "http", "url", "https://example.com/mcp"));
		Map<String, Object> root = new LinkedHashMap<>();
		root.put("servers", existing);
		node.put(MCP_PREFERENCE_KEY, MAPPER.writeValueAsString(root));
		node.flush();

		McpSettingsInjector.installForCopilot(jwtWithIssuer("https://iam.checkmarx.com"));

		Map<String, Object> servers = readRawServers();
		assertTrue(servers.containsKey("other-server"), "Pre-existing unrelated server entry should be preserved");
		assertTrue(servers.containsKey("checkmarx"));
	}

	@Test
	@DisplayName("uninstallFromCopilot returns false when no Checkmarx entry exists")
	void uninstallFromCopilotReturnsFalseWhenNotInstalled() throws Exception {
		assertFalse(McpSettingsInjector.uninstallFromCopilot());
	}

	@Test
	@DisplayName("uninstallFromCopilot removes an existing Checkmarx entry and returns true")
	void uninstallFromCopilotRemovesExistingEntry() throws Exception {
		McpSettingsInjector.installForCopilot(jwtWithIssuer("https://iam.checkmarx.com"));

		assertTrue(McpSettingsInjector.uninstallFromCopilot());
		assertFalse(readRawServers().containsKey("checkmarx"));
	}

	@Test
	@DisplayName("uninstallFromCopilot preserves other server entries while removing only Checkmarx's")
	void uninstallFromCopilotPreservesOtherServers() throws Exception {
		McpSettingsInjector.installForCopilot(jwtWithIssuer("https://iam.checkmarx.com"));
		Map<String, Object> servers = readRawServers();
		servers.put("other-server", Map.of("type", "http", "url", "https://example.com/mcp"));
		Map<String, Object> root = new LinkedHashMap<>();
		root.put("servers", servers);
		node.put(MCP_PREFERENCE_KEY, MAPPER.writeValueAsString(root));
		node.flush();

		McpSettingsInjector.uninstallFromCopilot();

		Map<String, Object> remaining = readRawServers();
		assertFalse(remaining.containsKey("checkmarx"));
		assertTrue(remaining.containsKey("other-server"));
	}
}
