package checkmarx.ast.eclipse.plugin.tests.unit.devassist.scanners.secrets;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.eclipse.core.resources.IProject;
import org.eclipse.jface.text.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.checkmarx.eclipse.devassist.scanners.secrets.SecretsScannerCommand;

import com.checkmarx.eclipse.devassist.scanners.secrets.SecretsScannerCommand;

/**
 * Unit tests for {@link SecretsScannerCommand}. There is no
 * dependency-injection constructor for this command, so a mocked
 * {@link IProject} is used and assertions are scoped to the
 * eligibility-check / no-op paths that never reach the network-backed scan.
 */
class SecretsScannerCommandTest {

	private IProject project;
	private SecretsScannerCommand command;

	@BeforeEach
	void setUp() {
		project = mock(IProject.class);
		when(project.getName()).thenReturn("TestProject");
		command = new SecretsScannerCommand(project);
	}

	@Test
	@DisplayName("Constructor wires configuration to SECRETS engine")
	void testConstructorInitializesFields() {
		assertEquals("SECRETS", command.getConfig().getEngineName());
	}

	@Test
	@DisplayName("initializeScanner completes without throwing")
	void testInitializeScanner() {
		assertDoesNotThrow(command::initializeScanner);
	}

	@Test
	@DisplayName("shouldScan accepts ordinary files and rejects manifest files")
	void shouldScanReflectsExclusionRules() {
		assertTrue(command.shouldScan("/repo/src/Main.java"));
		assertFalse(command.shouldScan("/repo/package.json"));
	}

	@Test
	@DisplayName("scan returns null for excluded manifest files")
	void scanReturnsNullForExcludedFile() {
		assertNull(command.scan("/repo/package.json", new Document("content")));
	}

	@Test
	@DisplayName("Dispose completes without throwing")
	void testDispose() {
		assertDoesNotThrow(command::dispose);
	}
}
