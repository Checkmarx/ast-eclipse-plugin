package checkmarx.ast.eclipse.plugin.tests.unit.devassist.configuration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.concurrent.CompletableFuture;

import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.checkmarx.eclipse.common.listener.IMcpInstallCallback;
import com.checkmarx.eclipse.common.preferences.Preferences;
import com.checkmarx.eclipse.common.utils.PluginConstants;

/**
 * Unit tests for {@link McpInstallService}. Scoped to the paths that are safe
 * to exercise without mocking - the guard clauses that return before ever
 * reaching {@code TenantSettingsProvider} (real network I/O), plus the
 * "not authenticated" callback path, which is naturally true in this test
 * environment since {@code Preferences.STORE}'s {@code credentialsValidated}
 * flag defaults to false and nothing in this module sets it.
 */
class McpInstallServiceTest {

	private static final String COPILOT_UI_BUNDLE_ID = "com.microsoft.copilot.eclipse.ui";
	private static final String MCP_PREFERENCE_KEY = "mcp";

	@BeforeEach
	@AfterEach
	void clearMcpPreference() throws Exception {
		IEclipsePreferences node = InstanceScope.INSTANCE.getNode(COPILOT_UI_BUNDLE_ID);
		node.remove(MCP_PREFERENCE_KEY);
		node.flush();
	}

	@Test
	@DisplayName("This test environment is unauthenticated by default (no test sets credentialsValidated)")
	void environmentIsUnauthenticatedByDefault() {
		assertFalse(Preferences.isAuthenticated());
	}

	@Test
	@DisplayName("attemptAutoInstall() is a safe no-op when the user is not authenticated")
	void attemptAutoInstallNoOpWhenNotAuthenticated() {
		assertDoesNotThrow(() -> McpInstallService.attemptAutoInstall());
	}

	@Test
	@DisplayName("attemptAutoInstall(apiKey, params) is a safe no-op for a null or blank apiKey")
	void attemptAutoInstallTwoArgNoOpForBlankApiKey() {
		assertDoesNotThrow(() -> McpInstallService.attemptAutoInstall(null, null));
		assertDoesNotThrow(() -> McpInstallService.attemptAutoInstall("  ", "params"));
	}

	@Test
	@DisplayName("installSilentlyAsync completes immediately with false for a null or blank credential")
	void installSilentlyAsyncCompletesFalseForBlankCredential() {
		CompletableFuture<Boolean> nullResult = McpInstallService.installSilentlyAsync(null);
		CompletableFuture<Boolean> blankResult = McpInstallService.installSilentlyAsync("  ");

		assertFalse(nullResult.join());
		assertFalse(blankResult.join());
	}

	@Test
	@DisplayName("installFromUi reports onFailure with the not-authenticated message when unauthenticated")
	void installFromUiReportsNotAuthenticated() {
		IMcpInstallCallback callback = mock(IMcpInstallCallback.class);

		McpInstallService.installFromUi(callback);

		verify(callback).onFailure(PluginConstants.MCP_NOT_AUTHENTICATED_MESSAGE);
		verify(callback, never()).onSuccess();
		verify(callback, never()).onAlreadyUpToDate();
	}

	@Test
	@DisplayName("uninstall() returns false when there is no MCP entry to remove")
	void uninstallReturnsFalseWhenNothingToRemove() {
		assertFalse(McpInstallService.uninstall());
	}

	@Test
	@DisplayName("uninstallSilentlyAsync completes with false when there is no MCP entry to remove")
	void uninstallSilentlyAsyncCompletesFalseWhenNothingToRemove() {
		assertFalse(McpInstallService.uninstallSilentlyAsync().join());
	}

	@Test
	@DisplayName("installSilentlyAsync with a real (non-network) credential actually installs the MCP entry")
	void installSilentlyAsyncInstallsWithRealCredential() {
		Boolean changed = McpInstallService.installSilentlyAsync("some-token").join();

		assertTrue(changed);
		assertTrue(McpInstallService.uninstall(), "The entry installed above should now be removable");
	}
}
