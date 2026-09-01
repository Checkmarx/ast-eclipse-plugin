package checkmarx.ast.eclipse.plugin.tests.unit.devassist.scanners.oss;

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
 * Unit tests for {@link OssScannerService} covering manifest-file eligibility
 * logic without touching the network-backed scan path.
 */
class OssScannerServiceTest {

	private OssScannerService service;

	@BeforeEach
	void setUp() {
		IProject project = mock(IProject.class);
		service = new OssScannerService(project);
	}

	@Test
	@DisplayName("createConfig builds OSS engine configuration")
	void testCreateConfig() {
		ScannerConfig config = OssScannerService.createConfig();
		assertEquals("OSS", config.getEngineName());
		assertEquals(DevAssistConstants.OSS_REALTIME_SCANNER, config.getConfigSection());
		assertEquals(DevAssistConstants.ACTIVATE_OSS_REALTIME_SCANNER, config.getActivateKey());
	}

	@Test
	@DisplayName("shouldScanFile accepts known manifest files across package managers")
	void shouldScanFileAcceptsKnownManifests() {
		assertTrue(service.shouldScanFile("/repo/pom.xml"), "Maven pom.xml");
		assertTrue(service.shouldScanFile("/repo/package.json"), "npm package.json");
		assertTrue(service.shouldScanFile("/repo/requirements.txt"), "Python requirements.txt");
		assertTrue(service.shouldScanFile("/repo/build.gradle"), "Gradle build.gradle");
		assertTrue(service.shouldScanFile("/repo/go.mod"), "Go go.mod");
		assertTrue(service.shouldScanFile("/repo/Gemfile"), "Ruby Gemfile");
		assertTrue(service.shouldScanFile("/repo/composer.json"), "PHP composer.json");
	}

	@Test
	@DisplayName("shouldScanFile rejects non-manifest files")
	void shouldScanFileRejectsNonManifestFiles() {
		assertFalse(service.shouldScanFile("/repo/Main.java"));
	}

	@Test
	@DisplayName("shouldScanFile rejects null path")
	void shouldScanFileRejectsNull() {
		assertFalse(service.shouldScanFile(null));
	}

	@Test
	@DisplayName("scanWithDocument returns null when file is not eligible")
	void scanWithDocumentReturnsNullWhenNotEligible() {
		assertNull(service.scanWithDocument("/repo/Main.java", new Document("content")));
	}

	@Test
	@DisplayName("close() completes without throwing")
	void closeDoesNotThrow() {
		assertDoesNotThrow(service::close);
	}
}
