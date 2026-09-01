package checkmarx.ast.eclipse.plugin.tests.unit.devassist.scanners.oss;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.eclipse.core.resources.IProject;
import org.eclipse.jface.text.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link OssScannerCommand}. There is no dependency-injection
 * constructor for this command, so a mocked {@link IProject} is used and
 * assertions are scoped to the eligibility-check / no-op paths that never
 * reach the network-backed scan.
 */
class OssScannerCommandTest {

	private IProject project;
	private OssScannerCommand command;

	@BeforeEach
	void setUp() {
		project = mock(IProject.class);
		when(project.getName()).thenReturn("TestProject");
		when(project.isOpen()).thenReturn(false);
		command = new OssScannerCommand(project);
	}

	@Test
	@DisplayName("Constructor wires scanner service and configuration")
	void testConstructorInitializesFields() {
		assertNotNull(command.ossScannerService);
		assertEquals("OSS", command.getConfig().getEngineName());
	}

	@Test
	@DisplayName("initializeScanner schedules its workspace scan job without throwing")
	void testInitializeScannerDoesNotThrow() {
		// project.isOpen() is stubbed false, so the scheduled job's traversal
		// short-circuits immediately - this only verifies scheduling itself is safe.
		assertDoesNotThrow(command::initializeScanner);
	}

	@Test
	@DisplayName("shouldScan accepts manifest files and rejects unrelated files")
	void shouldScanReflectsManifestEligibility() {
		assertTrue(command.shouldScan("/repo/pom.xml"));
		assertFalse(command.shouldScan("/repo/Main.java"));
	}

	@Test
	@DisplayName("scan(filePath, document) returns null for ineligible files")
	void scanWithDocumentReturnsNullForIneligibleFile() {
		assertNull(command.scan("/repo/Main.java", new Document("content")));
	}

	@Test
	@DisplayName("scan(filePath) returns null for ineligible files")
	void scanByPathReturnsNullForIneligibleFile() {
		assertNull(command.scan("/repo/Main.java"));
	}

	@Test
	@DisplayName("Dispose completes without throwing")
	void testDispose() {
		assertDoesNotThrow(command::dispose);
	}
}
