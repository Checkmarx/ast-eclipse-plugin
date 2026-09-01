package checkmarx.ast.eclipse.plugin.tests.unit.devassist.scanners.iac;

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
 * Unit tests for {@link IacScannerService} covering file-eligibility logic
 * without touching the network-backed scan path.
 */
class IacScannerServiceTest {

	private IacScannerService service;

	@BeforeEach
	void setUp() {
		IProject project = mock(IProject.class);
		service = new IacScannerService(project);
	}

	@Test
	@DisplayName("createConfig builds IaC engine configuration")
	void testCreateConfig() {
		ScannerConfig config = IacScannerService.createConfig();
		assertEquals("IAC", config.getEngineName());
		assertEquals(DevAssistConstants.IAC_REALTIME_SCANNER, config.getConfigSection());
		assertEquals(DevAssistConstants.ACTIVATE_IAC_REALTIME_SCANNER, config.getActivateKey());
	}

	@Test
	@DisplayName("shouldScanFile accepts Terraform files")
	void shouldScanFileAcceptsTerraform() {
		assertTrue(service.shouldScanFile("/repo/main.tf"));
	}

	@Test
	@DisplayName("shouldScanFile accepts yaml/yml/json files")
	void shouldScanFileAcceptsYamlYmlJson() {
		assertTrue(service.shouldScanFile("/repo/template.yaml"));
		assertTrue(service.shouldScanFile("/repo/template.yml"));
		assertTrue(service.shouldScanFile("/repo/data.json"));
	}

	@Test
	@DisplayName("shouldScanFile accepts dockerfile patterns")
	void shouldScanFileAcceptsDockerfilePatterns() {
		assertTrue(service.shouldScanFile("/repo/Dockerfile"));
		assertTrue(service.shouldScanFile("/repo/Dockerfile.dev"));
		assertTrue(service.shouldScanFile("/repo/custom.dockerfile"));
	}

	@Test
	@DisplayName("shouldScanFile rejects unsupported extensions")
	void shouldScanFileRejectsUnsupportedExtensions() {
		assertFalse(service.shouldScanFile("/repo/notes.txt"));
	}

	@Test
	@DisplayName("shouldScanFile rejects files without an extension and no dockerfile match")
	void shouldScanFileRejectsFilesWithoutExtension() {
		assertFalse(service.shouldScanFile("/repo/README"));
	}

	@Test
	@DisplayName("shouldScanFile rejects null or blank path")
	void shouldScanFileRejectsNullOrBlank() {
		assertFalse(service.shouldScanFile(null));
		assertFalse(service.shouldScanFile(""));
	}

	@Test
	@DisplayName("scan returns null when file is not eligible")
	void scanReturnsNullWhenNotEligible() {
		assertNull(service.scan("/repo/notes.txt", new Document("content"), mock(IProject.class)));
	}

	@Test
	@DisplayName("close() completes without throwing")
	void closeDoesNotThrow() {
		assertDoesNotThrow(service::close);
	}
}
