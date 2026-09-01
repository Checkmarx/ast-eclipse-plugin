package checkmarx.ast.eclipse.plugin.tests.unit.devassist.scanners.containers;

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
 * Unit tests for {@link ContainerScannerService} covering file-eligibility
 * logic (Dockerfile / Docker Compose / Helm detection) without touching the
 * network-backed scan path.
 */
class ContainerScannerServiceTest {

	private ContainerScannerService service;

	@BeforeEach
	void setUp() {
		IProject project = mock(IProject.class);
		service = new ContainerScannerService(project);
	}

	@Test
	@DisplayName("createConfig builds Containers engine configuration")
	void testCreateConfig() {
		ScannerConfig config = ContainerScannerService.createConfig();
		assertEquals("CONTAINERS", config.getEngineName());
		assertEquals(DevAssistConstants.CONTAINER_REALTIME_SCANNER, config.getConfigSection());
		assertEquals(DevAssistConstants.ACTIVATE_CONTAINER_REALTIME_SCANNER, config.getActivateKey());
	}

	@Test
	@DisplayName("shouldScanFile accepts a Dockerfile")
	void shouldScanFileAcceptsDockerfile() {
		assertTrue(service.shouldScanFile("/project/Dockerfile"));
	}

	@Test
	@DisplayName("shouldScanFile accepts any path containing 'dockerfile', by design of the fallback check")
	void shouldScanFileAcceptsAnyPathContainingDockerfileSubstring() {
		// NOTE: isContainersFilePatternMatching() falls back to
		// lowerPath.contains("dockerfile") regardless of whether any glob pattern
		// actually matched, so this also (perhaps unintentionally) accepts a file
		// that merely contains "dockerfile" in its name.
		assertTrue(service.shouldScanFile("/project/notes/mydockerfilebackup.txt"));
	}

	@Test
	@DisplayName("shouldScanFile accepts docker-compose yml/yaml variants")
	void shouldScanFileAcceptsDockerComposeVariants() {
		assertTrue(service.shouldScanFile("/project/docker-compose.yml"));
		assertTrue(service.shouldScanFile("/project/docker-compose-prod.yaml"));
	}

	@Test
	@DisplayName("shouldScanFile accepts .containerfile and .image extensions")
	void shouldScanFileAcceptsContainerfileAndImageExtensions() {
		assertTrue(service.shouldScanFile("/project/app.containerfile"));
		assertTrue(service.shouldScanFile("/project/app.image"));
	}

	@Test
	@DisplayName("shouldScanFile rejects unrelated file types")
	void shouldScanFileRejectsUnrelatedFiles() {
		assertFalse(service.shouldScanFile("/project/Main.java"));
	}

	@Test
	@DisplayName("isHelmFile accepts yaml files under a /helm/ directory")
	void isHelmFileAcceptsYamlUnderHelmDirectory() {
		assertTrue(service.isHelmFile("/project/helm/templates/deployment.yaml"));
	}

	@Test
	@DisplayName("isHelmFile rejects excluded chart/values files even under /helm/")
	void isHelmFileRejectsExcludedFiles() {
		assertFalse(service.isHelmFile("/project/helm/chart.yaml"));
		assertFalse(service.isHelmFile("/project/helm/values.yml"));
	}

	@Test
	@DisplayName("isHelmFile rejects yaml files outside a /helm/ directory")
	void isHelmFileRejectsYamlOutsideHelmDirectory() {
		assertFalse(service.isHelmFile("/project/config/service.yaml"));
	}

	@Test
	@DisplayName("isHelmFile rejects null path")
	void isHelmFileRejectsNull() {
		assertFalse(service.isHelmFile(null));
	}

	@Test
	@DisplayName("scan returns null when file is not eligible")
	void scanReturnsNullWhenNotEligible() {
		assertNull(service.scan("/project/Main.java", new Document("content"), mock(IProject.class)));
	}

	@Test
	@DisplayName("close() completes without throwing")
	void closeDoesNotThrow() {
		assertDoesNotThrow(service::close);
	}
}
