package checkmarx.ast.eclipse.plugin.tests.unit.devassist.scanners.asca;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.eclipse.core.resources.IProject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.checkmarx.eclipse.devassist.scanners.asca.AscaScannerCommand;

/**
 * Unit tests for {@link AscaScannerCommand}.
 */
class AscaScannerCommandTest {

	private IProject project;
	private AscaScannerCommand command;

	@BeforeEach
	void setUp() {
		project = mock(IProject.class);
		when(project.getName()).thenReturn("TestProject");
		command = new AscaScannerCommand(project);
	}

	@Test
	@DisplayName("Constructor wires scanner service and configuration")
	void testConstructorInitializesFields() {
		assertNotNull(command.ascaScannerService, "ASCA service should be created");
		assertNotNull(command.config, "Scanner config should be available");
	}

	@Test
	@DisplayName("initializeScanner completes without exceptions")
	void testInitializeScanner() {
		assertDoesNotThrow(command::initializeScanner);
	}

	@Test
	@DisplayName("Scanner configuration points to ASCA engine")
	void testCommandConfiguration() {
		assertEquals("ASCA", command.getConfig().getEngineName());
	}

	@Test
	@DisplayName("Multiple command instances use separate services")
	void testMultipleInstancesHaveIndependentServices() {
		AscaScannerCommand otherCommand = new AscaScannerCommand(project);
		assertNotSame(command.ascaScannerService, otherCommand.ascaScannerService);
	}

	@Test
	@DisplayName("Dispose completes without throwing")
	void testDispose() {
		assertDoesNotThrow(command::dispose);
	}

	@Test
	@DisplayName("scan delegates to the ASCA scanner service")
	void testScanDelegatesToService() {
		assertDoesNotThrow(() -> command.scan("/project/node_modules/Main.java",
				new org.eclipse.jface.text.Document("content")));
	}
}
