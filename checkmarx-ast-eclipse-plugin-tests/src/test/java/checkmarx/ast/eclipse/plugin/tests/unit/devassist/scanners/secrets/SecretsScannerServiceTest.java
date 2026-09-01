package checkmarx.ast.eclipse.plugin.tests.unit.devassist.scanners.secrets;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import org.eclipse.core.resources.IProject;
import org.eclipse.jface.text.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.checkmarx.eclipse.devassist.common.ScannerConfig;
import com.checkmarx.eclipse.devassist.utils.DevAssistConstants;

/**
 * Unit tests for {@link SecretsScannerService} covering the manifest-file
 * exclusion logic without touching the network-backed scan path.
 */
class SecretsScannerServiceTest {

	private SecretsScannerService service;

	@BeforeEach
	void setUp() {
		IProject project = mock(IProject.class);
		service = new SecretsScannerService(project);
	}

	@Test
	@DisplayName("createConfig builds Secrets engine configuration")
	void testCreateConfig() {
		ScannerConfig config = SecretsScannerService.createConfig();
		assertEquals("SECRETS", config.getEngineName());
		assertEquals(DevAssistConstants.SECRETS_REALTIME_SCANNER, config.getConfigSection());
		assertEquals(DevAssistConstants.ACTIVATE_SECRETS_REALTIME_SCANNER, config.getActivateKey());
	}

	@Test
	@DisplayName("shouldScanFile accepts ordinary source files")
	void shouldScanFileAcceptsOrdinaryFiles() {
		assertTrue(service.shouldScanFile("/repo/src/Main.java"));
		assertTrue(service.shouldScanFile("/repo/config.properties"));
	}

	@Test
	@DisplayName("shouldScanFile excludes known manifest/lock files")
	void shouldScanFileExcludesManifestFiles() {
		assertFalse(service.shouldScanFile("/repo/package.json"));
		assertFalse(service.shouldScanFile("/repo/pom.xml"));
		assertFalse(service.shouldScanFile("/repo/go.mod"));
		assertFalse(service.shouldScanFile("/repo/requirements.txt"));
		assertFalse(service.shouldScanFile("/repo/Gemfile"));
		assertFalse(service.shouldScanFile("/repo/Cargo.toml"));
		assertFalse(service.shouldScanFile("/repo/composer.json"));
		assertFalse(service.shouldScanFile("/repo/package-lock.json"));
		assertFalse(service.shouldScanFile("/repo/yarn.lock"));
	}

	@Test
	@DisplayName("shouldScanFile excludes Checkmarx ignore list files")
	void shouldScanFileExcludesCheckmarxIgnoreFiles() {
		assertFalse(service.shouldScanFile("/repo/.checkmarx/.checkmarxIgnored"));
		assertFalse(service.shouldScanFile("/repo/.checkmarx/.checkmarxIgnoredTempList.json"));
	}

	@Test
	@DisplayName("shouldScanFile rejects null or blank path")
	void shouldScanFileRejectsNullOrBlank() {
		assertFalse(service.shouldScanFile(null));
		assertFalse(service.shouldScanFile(""));
	}

	@Test
	@DisplayName("scan returns null when file is excluded")
	void scanReturnsNullWhenExcluded() {
		assertNull(service.scan("/repo/package.json", new Document("content"), mock(IProject.class)));
	}

	@Test
	@DisplayName("close() completes without throwing")
	void closeDoesNotThrow() {
		assertDoesNotThrow(service::close);
	}
}
