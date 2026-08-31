package com.checkmarx.eclipse.devassist.scanners.asca;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.lang.reflect.Method;

import org.eclipse.core.resources.IProject;
import org.eclipse.jface.text.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.checkmarx.eclipse.devassist.common.ScannerConfig;
import com.checkmarx.eclipse.devassist.utils.DevAssistConstants;

/**
 * Unit tests for {@link AscaScannerService}, focused on the pure-logic paths
 * (config creation, file-type support, filename sanitization) that don't
 * require a live Eclipse workbench.
 */
class AscaScannerServiceTest {

	private IProject project;
	private AscaScannerService service;

	@BeforeEach
	void setUp() {
		project = mock(IProject.class);
		service = new AscaScannerService(project);
	}

	@Test
	@DisplayName("createConfig builds ASCA engine configuration")
	void testCreateConfig() {
		ScannerConfig config = AscaScannerService.createConfig();
		assertEquals("ASCA", config.getEngineName());
		assertEquals(DevAssistConstants.ASCA_REALTIME_SCANNER, config.getConfigSection());
		assertEquals(DevAssistConstants.ACTIVATE_ASCA_REALTIME_SCANNER, config.getActivateKey());
	}

	@Test
	@DisplayName("shouldScanFile rejects paths under node_modules")
	void shouldScanFileRejectsNodeModules() {
		assertFalse(service.shouldScanFile("/project/node_modules/Main.java"));
	}

	@Test
	@DisplayName("shouldScanFile rejects unsupported extensions")
	void shouldScanFileRejectsUnsupportedExtension() {
		assertFalse(service.shouldScanFile("/project/Main.txt"));
	}

	@Test
	@DisplayName("shouldScanFile accepts every supported ASCA extension")
	void shouldScanFileAcceptsSupportedExtensions() {
		for (String ext : DevAssistConstants.ASCA_SUPPORTED_EXTENSIONS) {
			assertTrue(service.shouldScanFile("/project/Main." + ext), "Extension should be supported: " + ext);
		}
	}

	@Test
	@DisplayName("shouldScanFile returns false for null or empty path")
	void shouldScanFileRejectsNullOrEmpty() {
		assertFalse(service.shouldScanFile(null));
		assertFalse(service.shouldScanFile(""));
	}

	@Test
	@DisplayName("scanWithDocument returns null when file is not eligible")
	void scanWithDocumentReturnsNullWhenNotEligible() {
		assertNull(service.scanWithDocument("/project/Main.txt", new Document("content")));
	}

	@Test
	@DisplayName("sanitizeFileName strips dangerous characters")
	void sanitizeFileNameRemovesDangerousCharacters() throws Exception {
		Method sanitize = AscaScannerService.class.getDeclaredMethod("sanitizeFileName", String.class);
		sanitize.setAccessible(true);
		String sanitized = (String) sanitize.invoke(service, "../..\\evil:name?.java");
		assertFalse(sanitized.contains(".."));
		assertFalse(sanitized.contains("/"));
		assertFalse(sanitized.contains("\\"));
		assertFalse(sanitized.contains(":"));
		assertTrue(sanitized.endsWith(".java"));
	}

	@Test
	@DisplayName("sanitizeFileName falls back to default name for blank input")
	void sanitizeFileNameFallsBackForBlankInput() throws Exception {
		Method sanitize = AscaScannerService.class.getDeclaredMethod("sanitizeFileName", String.class);
		sanitize.setAccessible(true);
		assertEquals("temp_asca.tmp", sanitize.invoke(service, "   "));
		assertEquals("temp_asca.tmp", sanitize.invoke(service, (Object) null));
	}

	@Test
	@DisplayName("sanitizeFileName truncates overly long names while preserving extension")
	void sanitizeFileNameTruncatesLongNames() throws Exception {
		Method sanitize = AscaScannerService.class.getDeclaredMethod("sanitizeFileName", String.class);
		sanitize.setAccessible(true);
		String longName = "a".repeat(250) + ".java";
		String sanitized = (String) sanitize.invoke(service, longName);
		assertTrue(sanitized.length() <= 200);
		assertTrue(sanitized.endsWith(".java"));
	}

	@Test
	@DisplayName("close() completes without throwing")
	void closeDoesNotThrow() {
		assertNotNull(service);
		org.junit.jupiter.api.Assertions.assertDoesNotThrow(service::close);
	}
}
