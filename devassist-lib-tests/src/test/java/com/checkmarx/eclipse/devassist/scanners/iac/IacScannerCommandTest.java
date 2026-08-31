package com.checkmarx.eclipse.devassist.scanners.iac;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.eclipse.core.resources.IProject;
import org.eclipse.jface.text.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link IacScannerCommand}, using the dependency-injection
 * constructor to substitute a mocked {@link IacScannerService}.
 */
class IacScannerCommandTest {

	private IProject project;
	private IacScannerService scannerService;
	private IacScannerCommand command;

	@BeforeEach
	void setUp() throws Exception {
		project = mock(IProject.class);
		when(project.getName()).thenReturn("TestProject");
		scannerService = mock(IacScannerService.class);
		command = new IacScannerCommand(project, scannerService);
	}

	@Test
	@DisplayName("Constructor wires configuration to IAC engine")
	void testConstructorInitializesFields() {
		assertEquals("IAC", command.getConfig().getEngineName());
	}

	@Test
	@DisplayName("initializeScanner completes without throwing")
	void testInitializeScanner() {
		assertDoesNotThrow(command::initializeScanner);
	}

	@Test
	@DisplayName("shouldScan delegates to the underlying service")
	void shouldScanDelegatesToService() {
		when(scannerService.shouldScanFile("/repo/main.tf")).thenReturn(true);
		assertTrue(command.shouldScan("/repo/main.tf"));

		when(scannerService.shouldScanFile("/repo/Main.java")).thenReturn(false);
		assertFalse(command.shouldScan("/repo/Main.java"));
	}

	@Test
	@DisplayName("scan delegates to the underlying service with the project")
	void scanDelegatesToService() {
		Document document = new Document("content");
		command.scan("/repo/main.tf", document);
		verify(scannerService).scan("/repo/main.tf", document, project);
	}

	@Test
	@DisplayName("Dispose closes the underlying service without throwing")
	void testDisposeClosesService() throws Exception {
		assertDoesNotThrow(command::dispose);
		verify(scannerService).close();
	}
}
