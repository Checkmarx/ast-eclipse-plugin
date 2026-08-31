package com.checkmarx.eclipse.devassist.scanners.containers;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.eclipse.core.resources.IProject;
import org.eclipse.jface.text.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ContainerScannerCommand}, using the
 * dependency-injection constructor to substitute a mocked
 * {@link ContainerScannerService} and avoid any real scan/network path.
 */
class ContainerScannerCommandTest {

	private IProject project;
	private ContainerScannerService scannerService;
	private ContainerScannerCommand command;

	@BeforeEach
	void setUp() throws Exception {
		project = mock(IProject.class);
		when(project.getName()).thenReturn("TestProject");
		scannerService = mock(ContainerScannerService.class);
		command = new ContainerScannerCommand(project, scannerService);
	}

	@Test
	@DisplayName("Constructor wires the injected service and configuration")
	void testConstructorInitializesFields() {
		assertSame(scannerService, command.getScannerService());
		assertEquals("CONTAINERS", command.getConfig().getEngineName());
	}

	@Test
	@DisplayName("initializeScanner is idempotent and completes without throwing")
	void testInitializeScannerIdempotent() {
		assertDoesNotThrow(command::initializeScanner);
		assertDoesNotThrow(command::initializeScanner);
	}

	@Test
	@DisplayName("shouldScan delegates to the underlying service")
	void shouldScanDelegatesToService() {
		when(scannerService.shouldScanFile("/repo/Dockerfile")).thenReturn(true);
		assertTrue(command.shouldScan("/repo/Dockerfile"));

		when(scannerService.shouldScanFile("/repo/Main.java")).thenReturn(false);
		assertFalse(command.shouldScan("/repo/Main.java"));
	}

	@Test
	@DisplayName("scan returns null without invoking the service when file is not eligible")
	void scanShortCircuitsWhenNotEligible() {
		when(scannerService.shouldScanFile("/repo/Main.java")).thenReturn(false);

		assertNull(command.scan("/repo/Main.java", new Document("content")));
		verify(scannerService, never()).scan(org.mockito.ArgumentMatchers.anyString(),
				org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
	}

	@Test
	@DisplayName("scan delegates to the service when file is eligible")
	void scanDelegatesToServiceWhenEligible() {
		when(scannerService.shouldScanFile("/repo/Dockerfile")).thenReturn(true);
		Document document = new Document("FROM alpine");

		command.scan("/repo/Dockerfile", document);

		verify(scannerService).scan("/repo/Dockerfile", document, project);
	}

	@Test
	@DisplayName("Dispose closes the underlying service without throwing")
	void testDisposeClosesService() throws Exception {
		assertDoesNotThrow(command::dispose);
		verify(scannerService).close();
	}
}
